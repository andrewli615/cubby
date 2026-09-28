package com.cubby.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReceiptRequestTest {
    @Test
    void updateExposesOnlyEditableMetadata() {
        assertEquals(List.of("merchant", "purchaseDate", "total", "currency", "category"),
                Arrays.stream(UpdateReceiptRequest.class.getRecordComponents())
                        .map(component -> component.getName()).toList());
        var update = new UpdateReceiptRequest("Store", LocalDate.of(2026, 9, 28),
                BigDecimal.TEN, "USD", "Office");
        assertEquals("Office", update.category());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void createRejectsMissingImageKey(String imageKey) {
        assertThrows(IllegalArgumentException.class,
                () -> new CreateReceiptRequest("Store", LocalDate.of(2026, 9, 28),
                        BigDecimal.TEN, "USD", null, imageKey));
    }
}
