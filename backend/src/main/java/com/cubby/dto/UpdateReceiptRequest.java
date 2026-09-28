package com.cubby.dto;

import com.cubby.domain.ReceiptValidation;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Replaces editable metadata; a null category clears the category.
 * Identity, original image, processing status and timestamps are not client-editable.
 */
public record UpdateReceiptRequest(
        String merchant,
        LocalDate purchaseDate,
        BigDecimal total,
        String currency,
        String category) {

    public UpdateReceiptRequest {
        ReceiptValidation.metadata(merchant, purchaseDate, total, currency, category);
    }
}
