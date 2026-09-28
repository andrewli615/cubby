package com.cubby.repository;

import com.cubby.domain.Receipt;
import com.cubby.domain.ReceiptStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

final class ReceiptFixtures {
    static final UUID ID = UUID.fromString("920a995d-693c-4634-9fcf-985b1ddc0199");
    static final Instant CREATED = Instant.parse("2026-09-28T12:00:00Z");
    static final BigDecimal TOTAL = new BigDecimal("12345678901234567890.123456789");

    static Receipt receipt(String userId, String category, ReceiptStatus status) {
        return new Receipt(ID, userId, "Store", LocalDate.of(2026, 9, 28), TOTAL, "CAD",
                category, userId + "/image", status, CREATED, CREATED.plusSeconds(1));
    }

    /** Independent storage fixture, deliberately not produced by the mapper under test. */
    static Map<String, AttributeValue> item(String userId) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", string("USER#" + userId));
        item.put("SK", string("RECEIPT#" + ID));
        item.put("receiptId", string(ID.toString()));
        item.put("userId", string(userId));
        item.put("merchant", string("Store"));
        item.put("purchaseDate", string("2026-09-28"));
        item.put("total", AttributeValue.builder().n("12345678901234567890.123456789").build());
        item.put("currency", string("CAD"));
        item.put("category", string("Office"));
        item.put("imageKey", string(userId + "/image"));
        item.put("status", string("READY"));
        item.put("createdAt", string("2026-09-28T12:00:00Z"));
        item.put("updatedAt", string("2026-09-28T12:00:01Z"));
        return item;
    }

    static AttributeValue string(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
