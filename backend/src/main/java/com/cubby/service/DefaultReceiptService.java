package com.cubby.service;

import com.cubby.domain.Receipt;
import com.cubby.domain.ReceiptImageKeys;
import com.cubby.domain.ReceiptStatus;
import com.cubby.domain.ReceiptValidation;
import com.cubby.dto.CreateReceiptRequest;
import com.cubby.dto.UpdateReceiptRequest;
import com.cubby.dto.UploadUrlRequest;
import com.cubby.dto.UploadUrlResponse;
import com.cubby.repository.ReceiptRepository;
import com.cubby.repository.ReceiptWriteConflictException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Receipt business rules; all persistence is delegated to the injected repository. */
public final class DefaultReceiptService implements ReceiptService {
    private final ReceiptRepository repository;
    private final ReceiptUploads uploads;
    private final Clock clock;
    private final Supplier<UUID> receiptIds;

    public DefaultReceiptService(ReceiptRepository repository, ReceiptUploads uploads) {
        this(repository, uploads, Clock.systemUTC(), UUID::randomUUID);
    }

    public DefaultReceiptService(ReceiptRepository repository, ReceiptUploads uploads, Clock clock, Supplier<UUID> receiptIds) {
        this.repository = Objects.requireNonNull(repository);
        this.uploads = Objects.requireNonNull(uploads);
        this.clock = Objects.requireNonNull(clock);
        this.receiptIds = Objects.requireNonNull(receiptIds);
    }

    @Override
    public Receipt create(String userId, CreateReceiptRequest request) {
        ReceiptValidation.text(userId, "userId");
        ReceiptValidation.required(request, "request");
        ReceiptImageKeys.requireOwned(userId, request.imageKey());
        Instant now = clock.instant();
        Receipt receipt = new Receipt(receiptIds.get(), userId, request.merchant(),
                request.purchaseDate(), request.total(), request.currency(), request.category(),
                request.imageKey(), ReceiptStatus.UPLOADED, now, now);
        try {
            return owned(userId, repository.save(userId, receipt));
        } catch (ReceiptWriteConflictException exception) {
            throw new ReceiptConflictException(exception);
        }
    }

    @Override
    public Receipt update(String userId, UUID receiptId, UpdateReceiptRequest request) {
        ReceiptValidation.required(request, "request");
        Receipt existing = get(userId, receiptId).orElseThrow(ReceiptNotFoundException::new);
        Instant now = clock.instant();
        Instant updatedAt = now.isBefore(existing.updatedAt()) ? existing.updatedAt() : now;
        Receipt updated = new Receipt(existing.receiptId(), existing.userId(), request.merchant(),
                request.purchaseDate(), request.total(), request.currency(), request.category(),
                existing.imageKey(), existing.status(), existing.createdAt(), updatedAt,
                existing.ocrJobId(), existing.ocr());
        try {
            return owned(userId, repository.update(userId, updated));
        } catch (ReceiptWriteConflictException exception) {
            // Includes a deletion racing with the read above. Never recreate the missing receipt.
            throw new ReceiptConflictException(exception);
        }
    }

    @Override
    public Optional<Receipt> get(String userId, UUID receiptId) {
        requireKey(userId, receiptId);
        return repository.find(userId, receiptId).map(receipt -> {
            Receipt result = owned(userId, receipt);
            if (!receiptId.equals(result.receiptId())) {
                throw new IllegalStateException("Repository returned an unexpected receipt");
            }
            return result;
        });
    }

    @Override
    public List<Receipt> list(String userId) {
        return list(userId, ReceiptListQuery.DEFAULT);
    }

    @Override
    public List<Receipt> list(String userId, ReceiptListQuery query) {
        ReceiptValidation.text(userId, "userId");
        ReceiptValidation.required(query, "query");
        // The repository's single-user Query already follows every DynamoDB page.
        // Stream.sorted is stable, retaining that order when selected values compare equal.
        return repository.listByUser(userId).stream()
                .map(receipt -> owned(userId, receipt))
                .filter(receipt -> query.merchant() == null || receipt.merchant().toLowerCase(Locale.ROOT)
                        .contains(query.merchant().toLowerCase(Locale.ROOT)))
                .filter(receipt -> query.category() == null || receipt.category() != null
                        && receipt.category().equalsIgnoreCase(query.category()))
                .filter(receipt -> query.dateFrom() == null || !receipt.purchaseDate().isBefore(query.dateFrom()))
                .filter(receipt -> query.dateTo() == null || !receipt.purchaseDate().isAfter(query.dateTo()))
                .sorted(order(query.sort()))
                .toList();
    }

    private static Comparator<Receipt> order(ReceiptListQuery.Sort sort) {
        return switch (sort) {
            case DATE_DESC -> Comparator.comparing(Receipt::purchaseDate).reversed();
            case DATE_ASC -> Comparator.comparing(Receipt::purchaseDate);
            case MERCHANT_ASC -> Comparator.comparing(Receipt::merchant, String.CASE_INSENSITIVE_ORDER);
            case MERCHANT_DESC -> Comparator.comparing(Receipt::merchant, String.CASE_INSENSITIVE_ORDER).reversed();
            case TOTAL_ASC -> Comparator.comparing(Receipt::total);
            case TOTAL_DESC -> Comparator.comparing(Receipt::total).reversed();
        };
    }

    @Override
    public void delete(String userId, UUID receiptId) {
        requireKey(userId, receiptId);
        if (!repository.delete(userId, receiptId)) {
            throw new ReceiptNotFoundException();
        }
    }

    @Override
    public UploadUrlResponse createUploadUrl(String userId, UploadUrlRequest request) {
        ReceiptImageKeys.requireOwner(userId);
        ReceiptValidation.required(request, "request");
        return uploads.createUploadUrl(userId, request);
    }

    private static Receipt owned(String userId, Receipt receipt) {
        if (!userId.equals(receipt.userId())) {
            throw new IllegalStateException("Repository returned a receipt belonging to another user");
        }
        return receipt;
    }

    private static void requireKey(String userId, UUID receiptId) {
        ReceiptValidation.text(userId, "userId");
        ReceiptValidation.required(receiptId, "receiptId");
    }
}
