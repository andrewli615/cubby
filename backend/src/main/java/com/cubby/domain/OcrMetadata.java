package com.cubby.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Extracted values remain separate from user-entered receipt metadata. */
public record OcrMetadata(String merchant, LocalDate purchaseDate, BigDecimal total,
        String currency, boolean reviewRequired) {
    public OcrMetadata {
        if (total != null && total.signum() < 0) {
            throw new IllegalArgumentException("OCR total must not be negative");
        }
    }
}
