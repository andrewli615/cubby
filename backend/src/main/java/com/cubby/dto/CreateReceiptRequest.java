package com.cubby.dto;

import com.cubby.domain.ReceiptValidation;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Identity, status and timestamps are assigned by the service, never by the caller. */
public record CreateReceiptRequest(
        String merchant,
        LocalDate purchaseDate,
        BigDecimal total,
        String currency,
        String category,
        String imageKey) {

    public CreateReceiptRequest {
        ReceiptValidation.metadata(merchant, purchaseDate, total, currency, category);
        ReceiptValidation.text(imageKey, "imageKey");
    }
}
