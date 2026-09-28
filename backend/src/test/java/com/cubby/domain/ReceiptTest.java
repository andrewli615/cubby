package com.cubby.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReceiptTest {
    private static final UUID ID = UUID.fromString("920a995d-693c-4634-9fcf-985b1ddc0199");
    private static final Instant CREATED = Instant.parse("2026-09-28T12:00:00Z");

    @ParameterizedTest
    @EnumSource(ReceiptStatus.class)
    void retainsIdentityMetadataAndEveryStatus(ReceiptStatus status) {
        Receipt receipt = receipt(ID, "owner", "image", status, CREATED, CREATED.plusSeconds(1));

        assertEquals(ID, receipt.receiptId());
        assertEquals("owner", receipt.userId());
        assertEquals("Store", receipt.merchant());
        assertEquals("Office", receipt.category());
        assertEquals("image", receipt.imageKey());
        assertEquals(status, receipt.status());
        assertEquals(CREATED, receipt.createdAt());
        assertEquals(CREATED.plusSeconds(1), receipt.updatedAt());
        assertEquals(receipt, receipt(ID, "owner", "image", status, CREATED, CREATED.plusSeconds(1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"receiptId", "userId", "imageKey", "status", "createdAt", "updatedAt"})
    void rejectsMissingRequiredSnapshotFields(String field) {
        assertThrows(IllegalArgumentException.class, () -> receipt(
                field.equals("receiptId") ? null : ID,
                field.equals("userId") ? null : "owner",
                field.equals("imageKey") ? null : "image",
                field.equals("status") ? null : ReceiptStatus.UPLOADED,
                field.equals("createdAt") ? null : CREATED,
                field.equals("updatedAt") ? null : CREATED));
    }

    @Test
    void rejectsBlankOwnerAndImageKey() {
        assertThrows(IllegalArgumentException.class,
                () -> receipt(ID, " \t", "image", ReceiptStatus.READY, CREATED, CREATED));
        assertThrows(IllegalArgumentException.class,
                () -> receipt(ID, "owner", " \t", ReceiptStatus.READY, CREATED, CREATED));
    }

    @Test
    void rejectsUpdateTimestampBeforeCreation() {
        assertThrows(IllegalArgumentException.class,
                () -> receipt(ID, "owner", "image", ReceiptStatus.READY, CREATED, CREATED.minusNanos(1)));
    }

    private static Receipt receipt(UUID id, String owner, String imageKey, ReceiptStatus status,
            Instant created, Instant updated) {
        return new Receipt(id, owner, "Store", LocalDate.of(2026, 9, 28), new BigDecimal("19.99"),
                "CAD", "Office", imageKey, status, created, updated);
    }
}
