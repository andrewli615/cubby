package com.cubby.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Immutable receipt snapshot. Category is optional; all other values are required. */
public record Receipt(
        UUID receiptId,
        String userId,
        String merchant,
        LocalDate purchaseDate,
        BigDecimal total,
        String currency,
        String category,
        String imageKey,
        ReceiptStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public Receipt {
        ReceiptValidation.required(receiptId, "receiptId");
        ReceiptValidation.text(userId, "userId");
        ReceiptValidation.metadata(merchant, purchaseDate, total, currency, category);
        ReceiptValidation.text(imageKey, "imageKey");
        ReceiptValidation.required(status, "status");
        ReceiptValidation.required(createdAt, "createdAt");
        ReceiptValidation.required(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
    }
}
