package com.cubby.service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Validated GET /receipts query. Dates are inclusive; blank filters mean no filter. */
public record ReceiptListQuery(String merchant, String category, LocalDate dateFrom,
        LocalDate dateTo, Sort sort) {
    public enum Sort { DATE_DESC, DATE_ASC, MERCHANT_ASC, MERCHANT_DESC, TOTAL_ASC, TOTAL_DESC }

    private static final Set<String> KEYS = Set.of("merchant", "category", "dateFrom", "dateTo", "sort");
    public static final ReceiptListQuery DEFAULT = new ReceiptListQuery(null, null, null, null, Sort.DATE_DESC);

    public ReceiptListQuery {
        merchant = normalize(merchant, "merchant");
        category = normalize(category, "category");
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("dateFrom must not exceed dateTo");
        }
        if (sort == null) sort = Sort.DATE_DESC;
    }

    public static ReceiptListQuery parse(String raw) {
        if (raw == null || raw.isEmpty()) return DEFAULT;
        if (raw.length() > 1024) throw new IllegalArgumentException("Query is too long");
        Map<String, String> values = new HashMap<>();
        for (String pair : raw.split("&", -1)) {
            if (pair.isEmpty()) throw new IllegalArgumentException("Empty query parameter");
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = decode(parts.length == 2 ? parts[1] : "");
            if (!KEYS.contains(key) || values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Unknown or repeated query parameter");
            }
        }
        String sortValue = values.get("sort");
        Sort sort = Sort.DATE_DESC;
        if (sortValue != null && !sortValue.isBlank()) {
            try { sort = Sort.valueOf(sortValue.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid sort", invalid); }
        }
        return new ReceiptListQuery(values.get("merchant"), values.get("category"),
                date(values.get("dateFrom")), date(values.get("dateTo")), sort);
    }

    private static LocalDate date(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
            throw new IllegalArgumentException("Invalid ISO date");
        }
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException invalid) { throw new IllegalArgumentException("Invalid ISO date", invalid); }
    }

    private static String normalize(String value, String field) {
        if (value == null) return null;
        if (value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.length() > 200) {
            throw new IllegalArgumentException("Invalid " + field);
        }
        return trimmed;
    }

    private static String decode(String value) {
        try { return URLDecoder.decode(value, StandardCharsets.UTF_8); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid query encoding", invalid); }
    }
}
