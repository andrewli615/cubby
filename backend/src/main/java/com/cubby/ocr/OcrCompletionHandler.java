package com.cubby.ocr;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.cubby.repository.DynamoDbReceiptRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.textract.TextractClient;

/** SNS completion verifies job, original object and owner against the stored receipt. */
public final class OcrCompletionHandler implements RequestHandler<Map<String, Object>, Void> {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ReceiptOcrService service;

    public OcrCompletionHandler() {
        Region region = Region.of(System.getenv("AWS_REGION"));
        service = new ReceiptOcrService(new DynamoDbReceiptRepository(
                DynamoDbClient.builder().region(region).build(), System.getenv("RECEIPTS_TABLE_NAME")),
                new TextractExpenseClient(TextractClient.builder().region(region).build()),
                System.getenv("RECEIPT_IMAGES_BUCKET"), System.getenv("TEXTRACT_TOPIC_ARN"),
                System.getenv("TEXTRACT_ROLE_ARN"), Clock.systemUTC());
    }

    public OcrCompletionHandler(ReceiptOcrService service) { this.service = service; }

    @Override
    public Void handleRequest(Map<String, Object> event, Context context) {
        if (!(event.get("Records") instanceof List<?> records)) return null;
        for (Object entry : records) {
            if (!(entry instanceof Map<?, ?> record) || !(record.get("Sns") instanceof Map<?, ?> sns)
                    || !(sns.get("Message") instanceof String message)) continue;
            JsonNode body;
            try { body = JSON.readTree(message); }
            catch (IOException malformed) { continue; }
            String key = body.path("DocumentLocation").path("S3ObjectName").asText("");
            String bucket = body.path("DocumentLocation").path("S3Bucket").asText("");
            String tag = body.path("JobTag").asText("");
            int separator = key.indexOf("/originals/");
            if (separator <= 0 || tag.isBlank()) continue;
            UUID receiptId;
            try { receiptId = UUID.fromString(tag); }
            catch (IllegalArgumentException invalid) { continue; }
            service.complete(key.substring(0, separator), receiptId, key, bucket,
                    body.path("JobId").asText(""), body.path("API").asText(""),
                    body.path("Status").asText(""));
        }
        return null;
    }
}
