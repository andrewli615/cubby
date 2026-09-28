package com.cubby.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cubby.domain.Receipt;
import com.cubby.domain.ReceiptStatus;
import com.cubby.repository.ReceiptRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReceiptListServiceTest {
    private final ReceiptRepository repository = mock(ReceiptRepository.class);
    private final DefaultReceiptService service = new DefaultReceiptService(repository, mock(ReceiptUploads.class));
    private final Receipt a = receipt("alice", "Corner Shop", "Office", "2026-09-01", "10.00", 1);
    private final Receipt b = receipt("alice", "corner market", "Office", "2026-09-28", "20.00", 2);
    private final Receipt c = receipt("alice", "Bakery", null, "2026-09-28", "20.00", 3);

    @Test
    void combinesCaseInsensitiveMerchantAndCategoryWithInclusiveDateBounds() {
        when(repository.listByUser("alice")).thenReturn(List.of(a, b, c));
        var query = ReceiptListQuery.parse("merchant=CORNER&category=office"
                + "&dateFrom=2026-09-01&dateTo=2026-09-28&sort=date_asc");
        assertEquals(List.of(a, b), service.list("alice", query));
        verify(repository).listByUser("alice");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void defaultAndEqualValueSortsAreStable() {
        when(repository.listByUser("alice")).thenReturn(List.of(a, b, c));
        assertEquals(List.of(b, c, a), service.list("alice"));
        assertEquals(List.of(a, b, c), service.list("alice", ReceiptListQuery.parse("sort=merchant_desc")));
        assertEquals(List.of(a, b, c), service.list("alice", ReceiptListQuery.parse("sort=total_asc")));
        assertEquals(List.of(b, c, a), service.list("alice", ReceiptListQuery.parse("sort=total_desc")));
    }

    @Test
    void merchantSortKeepsRepositoryOrderForEqualCaseInsensitiveValues() {
        Receipt equal = receipt("alice", "corner shop", "Office", "2026-09-02", "30.00", 4);
        when(repository.listByUser("alice")).thenReturn(List.of(a, equal, b));
        assertEquals(List.of(b, a, equal),
                service.list("alice", ReceiptListQuery.parse("sort=merchant_asc")));
    }

    @Test
    void emptyMatchesAndMissingCategoryReturnEmptyWithoutAnotherUserQuery() {
        when(repository.listByUser("alice")).thenReturn(List.of(a, b, c));
        assertEquals(List.of(), service.list("alice", ReceiptListQuery.parse("category=Travel")));
        assertEquals(List.of(), service.list("alice", ReceiptListQuery.parse("dateFrom=2027-01-01")));
        verify(repository, times(2)).listByUser("alice");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void refusesForeignRowsBeforeFilteringCanHideThem() {
        when(repository.listByUser("alice")).thenReturn(List.of(a, receipt("bob", "Other", null,
                "2026-09-01", "1.00", 4)));
        assertThrows(IllegalStateException.class,
                () -> service.list("alice", ReceiptListQuery.parse("merchant=Corner")));
        verify(repository).listByUser("alice");
    }

    private static Receipt receipt(String owner, String merchant, String category,
            String date, String total, int suffix) {
        UUID id = UUID.fromString("920a995d-693c-4634-9fcf-985b1ddc019" + suffix);
        Instant now = Instant.parse("2026-09-28T12:00:00Z");
        return new Receipt(id, owner, merchant, LocalDate.parse(date), new BigDecimal(total),
                "CAD", category, owner + "/originals/" + id, ReceiptStatus.READY, now, now);
    }
}
