package com.cubby.repository;

import static com.cubby.repository.ReceiptFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.cubby.domain.ReceiptStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class ReceiptItemMapperTest {
    @Test
    void mapsEveryFieldUsingNativeNumberAndUserPartitionedKeys() {
        assertEquals(item("alice"), ReceiptItemMapper.toItem(receipt("alice", "Office", ReceiptStatus.READY)));
        assertEquals(receipt("alice", "Office", ReceiptStatus.READY),
                ReceiptItemMapper.fromItem(item("alice"), "alice"));
    }

    @ParameterizedTest
    @EnumSource(ReceiptStatus.class)
    void roundTripsEveryStatusWithoutLosingDecimalPrecision(ReceiptStatus status) {
        var receipt = receipt("alice", "Office", status);
        assertEquals(receipt, ReceiptItemMapper.fromItem(ReceiptItemMapper.toItem(receipt), "alice"));
    }

    @Test
    void supportsAbsentAndExplicitNullCategory() {
        var receipt = receipt("alice", null, ReceiptStatus.UPLOADED);
        var encoded = ReceiptItemMapper.toItem(receipt);
        assertFalse(encoded.containsKey("category"));
        assertEquals(receipt, ReceiptItemMapper.fromItem(encoded, "alice"));
        var stored = item("alice");
        stored.put("category", AttributeValue.builder().nul(true).build());
        assertNull(ReceiptItemMapper.fromItem(stored, "alice").category());
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "PK", "SK"})
    void rejectsInconsistentOwnershipAndKeys(String field) {
        var stored = item("alice");
        stored.put(field, string("someone-else"));
        assertThrows(IllegalStateException.class, () -> ReceiptItemMapper.fromItem(stored, "alice"));
    }

    @Test
    void rejectsAnotherUsersItem() {
        assertThrows(IllegalStateException.class, () -> ReceiptItemMapper.fromItem(item("bob"), "alice"));
    }

    @Test
    void rejectsMissingAndMistypedData() {
        assertThrows(IllegalStateException.class, () -> ReceiptItemMapper.fromItem(Map.of(), "alice"));
        var stored = item("alice");
        stored.put("total", string("19.99"));
        assertThrows(IllegalStateException.class, () -> ReceiptItemMapper.fromItem(stored, "alice"));
        stored.remove("total");
        assertThrows(IllegalStateException.class, () -> ReceiptItemMapper.fromItem(stored, "alice"));
    }
}
