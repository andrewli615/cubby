package com.cubby.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cubby.dto.CreateReceiptRequest;
import com.cubby.dto.UpdateReceiptRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReceiptValidationTest {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 28);
    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "19.99", "12345678901234567890.123456789"})
    void preservesNonnegativeTotalsWithoutRounding(String amount) {
        BigDecimal total = new BigDecimal(amount);
        var create = new CreateReceiptRequest("Store", DATE, total, "CAD", null, "image");
        var update = new UpdateReceiptRequest("Store", DATE, total, "CAD", null);
        var receipt = receipt("Store", DATE, total, "CAD", null);

        assertEquals(total, create.total());
        assertEquals(total, update.total());
        assertEquals(total, receipt.total());
        assertEquals(DATE, receipt.purchaseDate());
        assertEquals("CAD", receipt.currency());
        assertNull(receipt.category());
    }

    @ParameterizedTest
    @MethodSource("invalidMetadata")
    void rejectsInvalidMetadataInModelAndBothRequests(String merchant, LocalDate date,
            BigDecimal total, String currency, String category) {
        assertThrows(IllegalArgumentException.class,
                () -> receipt(merchant, date, total, currency, category));
        assertThrows(IllegalArgumentException.class,
                () -> new CreateReceiptRequest(merchant, date, total, currency, category, "image"));
        assertThrows(IllegalArgumentException.class,
                () -> new UpdateReceiptRequest(merchant, date, total, currency, category));
    }

    static Stream<Arguments> invalidMetadata() {
        return Stream.of(
                Arguments.of(null, DATE, BigDecimal.ONE, "CAD", null),
                Arguments.of("", DATE, BigDecimal.ONE, "CAD", null),
                Arguments.of(" \t\n", DATE, BigDecimal.ONE, "CAD", null),
                Arguments.of("Store", null, BigDecimal.ONE, "CAD", null),
                Arguments.of("Store", DATE, null, "CAD", null),
                Arguments.of("Store", DATE, new BigDecimal("-0.001"), "CAD", null),
                Arguments.of("Store", DATE, new BigDecimal("-10"), "CAD", null),
                Arguments.of("Store", DATE, BigDecimal.ONE, null, null),
                Arguments.of("Store", DATE, BigDecimal.ONE, " ", null),
                Arguments.of("Store", DATE, BigDecimal.ONE, "cad", null),
                Arguments.of("Store", DATE, BigDecimal.ONE, "ZZZ", null),
                Arguments.of("Store", DATE, BigDecimal.ONE, "CA", null),
                Arguments.of("Store", DATE, BigDecimal.ONE, "CAD", ""),
                Arguments.of("Store", DATE, BigDecimal.ONE, "CAD", " \t"));
    }

    private static Receipt receipt(String merchant, LocalDate date, BigDecimal total,
            String currency, String category) {
        return new Receipt(UUID.randomUUID(), "owner", merchant, date, total, currency,
                category, "image", ReceiptStatus.UPLOADED, NOW, NOW);
    }
}
