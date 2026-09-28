package com.cubby.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReceiptListQueryTest {
    @Test
    void parsesDecodedFiltersInclusiveDatesAndSort() {
        var query = ReceiptListQuery.parse("merchant=Corner+Shop&category=Office%20Supplies"
                + "&dateFrom=2026-01-01&dateTo=2026-12-31&sort=total_desc");
        assertEquals("Corner Shop", query.merchant());
        assertEquals("Office Supplies", query.category());
        assertEquals(LocalDate.of(2026, 1, 1), query.dateFrom());
        assertEquals(LocalDate.of(2026, 12, 31), query.dateTo());
        assertEquals(ReceiptListQuery.Sort.TOTAL_DESC, query.sort());
    }

    @Test
    void blanksAreUnfilteredAndDefaultToNewestDate() {
        assertEquals(ReceiptListQuery.DEFAULT, ReceiptListQuery.parse(null));
        assertEquals(ReceiptListQuery.DEFAULT, ReceiptListQuery.parse(""));
        assertEquals(ReceiptListQuery.DEFAULT,
                ReceiptListQuery.parse("merchant=+&category=&dateFrom=&dateTo=&sort="));
        assertEquals(LocalDate.of(2026, 9, 28),
                ReceiptListQuery.parse("dateFrom=2026-09-28&dateTo=2026-09-28").dateTo());
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId=bob", "unknown=x", "sort=price", "sort=date_desc&sort=date_asc",
            "merchant=x&merchant=y", "merchant=%ZZ", "dateFrom=2026-02-30", "dateTo=yesterday",
            "dateFrom=2026-09-29&dateTo=2026-09-28", "merchant=a&&category=b", "merchant=%00x"})
    void rejectsInvalidQueries(String raw) {
        assertThrows(IllegalArgumentException.class, () -> ReceiptListQuery.parse(raw));
    }
}
