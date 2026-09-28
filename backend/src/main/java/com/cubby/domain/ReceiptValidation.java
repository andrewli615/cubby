package com.cubby.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;

/** Shared value rules for receipts and their immutable request DTOs. */
public final class ReceiptValidation {
    private ReceiptValidation() {}

    public static void required(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    public static void text(String value, String field) {
        required(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    public static void metadata(String merchant, LocalDate purchaseDate,
            BigDecimal total, String currency, String category) {
        text(merchant, "merchant");
        required(purchaseDate, "purchaseDate");
        required(total, "total");
        if (total.signum() < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        text(currency, "currency");
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be an uppercase ISO 4217 code");
        }
        Currency.getInstance(currency);
        if (category != null) {
            text(category, "category");
        }
    }
}
