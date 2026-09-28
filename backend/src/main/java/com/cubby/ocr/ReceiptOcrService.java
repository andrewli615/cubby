package com.cubby.ocr;

import com.cubby.domain.OcrMetadata;
import com.cubby.domain.Receipt;
import com.cubby.domain.ReceiptImageKeys;
import com.cubby.domain.ReceiptStatus;
import com.cubby.repository.ReceiptRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import software.amazon.awssdk.services.textract.model.BadDocumentException;
import software.amazon.awssdk.services.textract.model.DocumentTooLargeException;
import software.amazon.awssdk.services.textract.model.InvalidParameterException;
import software.amazon.awssdk.services.textract.model.InvalidS3ObjectException;
import software.amazon.awssdk.services.textract.model.UnsupportedDocumentException;

/** Asynchronous OCR state machine; transient SDK failures propagate for event retries. */
public final class ReceiptOcrService {
    private final ReceiptRepository receipts;
    private final ExpenseOcrClient textract;
    private final String bucket;
    private final String topicArn;
    private final String roleArn;
    private final Clock clock;

    public ReceiptOcrService(ReceiptRepository receipts, ExpenseOcrClient textract,
            String bucket, String topicArn, String roleArn, Clock clock) {
        this.receipts = Objects.requireNonNull(receipts);
        this.textract = Objects.requireNonNull(textract);
        this.bucket = Objects.requireNonNull(bucket);
        this.topicArn = Objects.requireNonNull(topicArn);
        this.roleArn = Objects.requireNonNull(roleArn);
        this.clock = Objects.requireNonNull(clock);
    }

    public void start(String userId, UUID receiptId) {
        Receipt receipt = receipts.find(userId, receiptId).orElse(null);
        if (receipt == null || receipt.status() != ReceiptStatus.UPLOADED) return;
        if (!validOriginal(receipt)) {
            transition(receipt, ReceiptStatus.UPLOADED, null, ReceiptStatus.OCR_FAILED, null, null);
            return;
        }
        String jobId;
        try {
            // A deterministic token collapses duplicate stream deliveries into one Textract job.
            jobId = textract.start(bucket, receipt.imageKey(), token(userId, receiptId),
                    receiptId.toString(), topicArn, roleArn);
        } catch (BadDocumentException | DocumentTooLargeException | InvalidParameterException
                | InvalidS3ObjectException | UnsupportedDocumentException invalid) {
            transition(receipt, ReceiptStatus.UPLOADED, null, ReceiptStatus.OCR_FAILED, null, null);
            return;
        }
        if (jobId == null || jobId.isBlank()) throw new IllegalStateException("Textract returned no job ID");
        transition(receipt, ReceiptStatus.UPLOADED, null, ReceiptStatus.PROCESSING, jobId, null);
    }

    public void complete(String userId, UUID receiptId, String key, String sourceBucket,
            String jobId, String api, String status) {
        if (!bucket.equals(sourceBucket) || !"StartExpenseAnalysis".equals(api) || jobId == null) return;
        Receipt receipt = receipts.find(userId, receiptId).orElse(null);
        if (receipt == null || !validOriginal(receipt) || !receipt.imageKey().equals(key)) return;
        // SNS may arrive before StartExpenseAnalysis's job ID is committed to DynamoDB.
        if (receipt.status() == ReceiptStatus.UPLOADED) throw new IllegalStateException("OCR start is not committed yet");
        if (receipt.status() != ReceiptStatus.PROCESSING || !jobId.equals(receipt.ocrJobId())) return;
        if ("FAILED".equals(status)) {
            transition(receipt, ReceiptStatus.PROCESSING, jobId, ReceiptStatus.OCR_FAILED, null, null);
            return;
        }
        if (!"SUCCEEDED".equals(status) && !"PARTIAL_SUCCESS".equals(status)) return;
        List<ExpenseOcrClient.Document> documents = new ArrayList<>();
        boolean partial = "PARTIAL_SUCCESS".equals(status);
        Set<String> seenTokens = new HashSet<>();
        String token = null;
        do {
            ExpenseOcrClient.Page page = textract.page(jobId, token);
            if ("FAILED".equals(page.status())) {
                transition(receipt, ReceiptStatus.PROCESSING, jobId, ReceiptStatus.OCR_FAILED, null, null);
                return;
            }
            if (!"SUCCEEDED".equals(page.status()) && !"PARTIAL_SUCCESS".equals(page.status())) {
                throw new IllegalStateException("Textract result is not ready");
            }
            if ("PARTIAL_SUCCESS".equals(page.status())) partial = true;
            documents.addAll(page.documents());
            token = page.nextToken();
            if (token != null && !seenTokens.add(token)) throw new IllegalStateException("Repeated Textract page token");
        } while (token != null);
        OcrMetadata metadata = extract(documents, partial);
        transition(receipt, ReceiptStatus.PROCESSING, jobId,
                metadata.reviewRequired() ? ReceiptStatus.REVIEW_NEEDED : ReceiptStatus.READY, null, metadata);
    }

    private void transition(Receipt receipt, ReceiptStatus from, String expectedJobId,
            ReceiptStatus to, String newJobId, OcrMetadata metadata) {
        Instant now = clock.instant();
        receipts.transitionOcr(receipt.userId(), receipt.receiptId(), receipt.imageKey(),
                from, expectedJobId, to, newJobId, metadata,
                now.isBefore(receipt.updatedAt()) ? receipt.updatedAt() : now);
    }

    private static boolean validOriginal(Receipt receipt) {
        try {
            ReceiptImageKeys.requireOwned(receipt.userId(), receipt.imageKey());
            String prefix = receipt.userId() + "/originals/";
            if (!receipt.imageKey().startsWith(prefix)) return false;
            UUID id = UUID.fromString(receipt.imageKey().substring(prefix.length()));
            return receipt.imageKey().equals(ReceiptImageKeys.original(receipt.userId(), id));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static OcrMetadata extract(List<ExpenseOcrClient.Document> documents, boolean partial) {
        Map<String, ExpenseOcrClient.Field> fields = new HashMap<>();
        boolean ambiguous = partial || documents.size() != 1;
        for (var document : documents) {
            for (var field : document.fields()) {
                if (!Set.of("VENDOR_NAME", "INVOICE_RECEIPT_DATE", "TOTAL", "CURRENCY")
                        .contains(field.type())) continue;
                if (fields.putIfAbsent(field.type(), field) != null || field.confidence() < 85) ambiguous = true;
            }
        }
        String merchant = value(fields, "VENDOR_NAME");
        String dateText = value(fields, "INVOICE_RECEIPT_DATE");
        String totalText = value(fields, "TOTAL");
        String currency = value(fields, "CURRENCY");
        LocalDate date = null;
        BigDecimal total = null;
        try { if (dateText != null) date = LocalDate.parse(dateText); }
        catch (DateTimeParseException invalid) { ambiguous = true; }
        try {
            if (totalText != null) total = new BigDecimal(totalText.replaceAll("[^0-9.-]", ""));
            if (total != null && total.signum() < 0) { total = null; ambiguous = true; }
        } catch (NumberFormatException invalid) { ambiguous = true; }
        if (merchant == null || date == null || total == null || currency == null) ambiguous = true;
        return new OcrMetadata(merchant, date, total, currency, ambiguous);
    }

    private static String value(Map<String, ExpenseOcrClient.Field> fields, String type) {
        var field = fields.get(type);
        return field == null || field.value() == null || field.value().isBlank() ? null : field.value();
    }

    private static String token(String userId, UUID receiptId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((userId + "\0" + receiptId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash); // Textract ClientRequestToken allows 64 characters.
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
