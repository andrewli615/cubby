package com.cubby.repository;

import com.cubby.domain.Receipt;
import com.cubby.domain.ReceiptStatus;
import com.cubby.domain.ReceiptValidation;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/** DynamoDB serialization stays outside the domain model. */
final class ReceiptItemMapper {
    private ReceiptItemMapper() {}

    static Map<String, AttributeValue> key(String userId, UUID receiptId) {
        ReceiptValidation.text(userId, "userId");
        ReceiptValidation.required(receiptId, "receiptId");
        return Map.of("PK", string("USER#" + userId), "SK", string("RECEIPT#" + receiptId));
    }

    static Map<String, AttributeValue> toItem(Receipt receipt) {
        Map<String, AttributeValue> item = new HashMap<>(key(receipt.userId(), receipt.receiptId()));
        item.put("receiptId", string(receipt.receiptId().toString()));
        item.put("userId", string(receipt.userId()));
        item.put("merchant", string(receipt.merchant()));
        item.put("purchaseDate", string(receipt.purchaseDate().toString()));
        item.put("total", AttributeValue.builder().n(receipt.total().toPlainString()).build());
        item.put("currency", string(receipt.currency()));
        if (receipt.category() != null) {
            item.put("category", string(receipt.category()));
        }
        item.put("imageKey", string(receipt.imageKey()));
        item.put("status", string(receipt.status().name()));
        item.put("createdAt", string(receipt.createdAt().toString()));
        item.put("updatedAt", string(receipt.updatedAt().toString()));
        return Map.copyOf(item);
    }

    static Receipt fromItem(Map<String, AttributeValue> item, String expectedUserId) {
        String userId = text(item, "userId");
        UUID receiptId = UUID.fromString(text(item, "receiptId"));
        Map<String, AttributeValue> expectedKey = key(expectedUserId, receiptId);
        if (!expectedUserId.equals(userId) || !expectedKey.get("PK").equals(item.get("PK"))
                || !expectedKey.get("SK").equals(item.get("SK"))) {
            throw new IllegalStateException("Receipt item does not match the requested user partition");
        }
        AttributeValue total = item.get("total");
        if (total == null || total.n() == null) {
            throw new IllegalStateException("Receipt item is missing numeric total");
        }
        AttributeValue category = item.get("category");
        return new Receipt(receiptId, userId, text(item, "merchant"),
                LocalDate.parse(text(item, "purchaseDate")), new BigDecimal(total.n()),
                text(item, "currency"),
                category == null || Boolean.TRUE.equals(category.nul()) ? null : text(item, "category"),
                text(item, "imageKey"), ReceiptStatus.valueOf(text(item, "status")),
                Instant.parse(text(item, "createdAt")), Instant.parse(text(item, "updatedAt")));
    }

    static AttributeValue string(String value) {
        return AttributeValue.builder().s(value).build();
    }

    private static String text(Map<String, AttributeValue> item, String field) {
        AttributeValue value = item.get(field);
        if (value == null || value.s() == null) {
            throw new IllegalStateException("Receipt item is missing string " + field);
        }
        return value.s();
    }
}
