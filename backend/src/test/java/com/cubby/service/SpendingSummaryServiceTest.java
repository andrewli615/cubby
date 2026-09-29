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

class SpendingSummaryServiceTest {
    private final ReceiptRepository repository = mock(ReceiptRepository.class);
    private final DefaultReceiptService service = new DefaultReceiptService(repository, mock(ReceiptUploads.class));

    @Test
    void filtersInclusiveDatesAndBuildsConservingFlowsPerCurrency() {
        var before = receipt("alice", "Before", "Office", "CAD", "2026-08-31", "100.00", 1);
        var start = receipt("alice", "Office Store", "Office", "CAD", "2026-09-01", "12.50", 2);
        var end = receipt("alice", "Travel Shop", null, "CAD", "2026-09-30", "7.50", 3);
        var usd = receipt("alice", "Airline", "Travel", "USD", "2026-09-15", "4.25", 4);
        var after = receipt("alice", "After", "Office", "CAD", "2026-10-01", "200.00", 5);
        when(repository.listByUser("alice")).thenReturn(List.of(before, start, end, usd, after));

        var summary = service.spending("alice", LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30"));

        assertEquals(List.of("CAD", "USD"), summary.currencies().stream().map(flow -> flow.currency()).toList());
        var cad = summary.currencies().get(0);
        assertMoney("20.00", cad.total());
        assertTrue(cad.nodes().stream().anyMatch(node -> node.label().equals("Uncategorized")));
        assertMoney("20.00", cad.links().stream().filter(link -> link.source().equals("spending"))
                .map(link -> link.value()).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertMoney("20.00", cad.links().stream().filter(link -> link.target().startsWith("merchant:"))
                .map(link -> link.value()).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertMoney("4.25", summary.currencies().get(1).total());
        verify(repository).listByUser("alice");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void emptySummaryHasNoCurrencyEntries() {
        when(repository.listByUser("alice")).thenReturn(List.of());
        assertTrue(service.spending("alice", null, null).currencies().isEmpty());
    }

    @Test
    void rejectsForeignPartitionRowsInsteadOfAggregatingThem() {
        when(repository.listByUser("alice")).thenReturn(List.of(receipt("bob", "Other", "Office", "CAD",
                "2026-09-01", "1.00", 6)));
        assertThrows(IllegalStateException.class, () -> service.spending("alice", null, null));
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private static Receipt receipt(String owner, String merchant, String category, String currency,
            String date, String total, int suffix) {
        UUID id = UUID.fromString("920a995d-693c-4634-9fcf-985b1ddc019" + suffix);
        Instant now = Instant.parse("2026-09-28T12:00:00Z");
        return new Receipt(id, owner, merchant, LocalDate.parse(date), new BigDecimal(total),
                currency, category, owner + "/originals/" + id, ReceiptStatus.READY, now, now);
    }
}
