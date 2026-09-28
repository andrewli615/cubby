package com.cubby.ocr;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cubby.domain.OcrMetadata;
import com.cubby.domain.Receipt;
import com.cubby.domain.ReceiptImageKeys;
import com.cubby.domain.ReceiptStatus;
import com.cubby.repository.ReceiptRepository;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.textract.model.BadDocumentException;

class ReceiptOcrServiceTest {
    private static final UUID ID = UUID.fromString("920a995d-693c-4634-9fcf-985b1ddc0199");
    private static final String KEY = ReceiptImageKeys.original("alice", ID);
    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private final ReceiptRepository receipts = mock(ReceiptRepository.class);
    private final ExpenseOcrClient textract = mock(ExpenseOcrClient.class);
    private final ReceiptOcrService service = new ReceiptOcrService(receipts, textract,
            "private-bucket", "topic-arn", "role-arn", Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));

    @Test
    void startsOnceWithOwnedOriginalAndDeterministicToken() {
        when(receipts.find("alice", ID)).thenReturn(Optional.of(receipt(ReceiptStatus.UPLOADED, null)));
        when(textract.start(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("job-1");
        service.start("alice", ID);
        var token = ArgumentCaptor.forClass(String.class);
        verify(textract).start(eq("private-bucket"), eq(KEY), token.capture(), eq(ID.toString()),
                eq("topic-arn"), eq("role-arn"));
        assertTrue(token.getValue().matches("[0-9a-f]{64}"));
        verify(receipts).transitionOcr("alice", ID, KEY, ReceiptStatus.UPLOADED, null,
                ReceiptStatus.PROCESSING, "job-1", null, NOW.plusSeconds(1));
    }

    @Test
    void duplicateStartsAndCompletionsCannotRegressState() {
        when(receipts.find("alice", ID)).thenReturn(Optional.of(receipt(ReceiptStatus.PROCESSING, "job-1")));
        service.start("alice", ID);
        when(receipts.find("alice", ID)).thenReturn(Optional.of(receipt(ReceiptStatus.READY, "job-1")));
        service.complete("alice", ID, KEY, "private-bucket", "job-1", "StartExpenseAnalysis", "SUCCEEDED");
        verifyNoInteractions(textract);
        verify(receipts, never()).transitionOcr(anyString(), any(), anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void repeatedUploadedEventsUseTheSameOwnerScopedStartToken() {
        when(receipts.find("alice", ID)).thenReturn(Optional.of(receipt(ReceiptStatus.UPLOADED, null)));
        when(textract.start(anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("job-1");
        service.start("alice", ID);
        service.start("alice", ID);
        var tokens = ArgumentCaptor.forClass(String.class);
        verify(textract, times(2)).start(eq("private-bucket"), eq(KEY), tokens.capture(), eq(ID.toString()),
                eq("topic-arn"), eq("role-arn"));
        assertEquals(tokens.getAllValues().get(0), tokens.getAllValues().get(1));
        assertTrue(tokens.getValue().matches("[0-9a-f]{64}"));
    }

    @Test
    void successPaginatesAndPersistsExtractedValuesWithoutReplacingUserValues() {
        when(receipts.find("alice", ID)).thenReturn(Optional.of(receipt(ReceiptStatus.PROCESSING, "job-1")));
        when(textract.page("job-1", null)).thenReturn(new ExpenseOcrClient.Page("SUCCEEDED",
                List.of(new ExpenseOcrClient.Document(List.of(
                        new ExpenseOcrClient.Field("VENDOR_NAME", "Market", 97),
                        new ExpenseOcrClient.Field("INVOICE_RECEIPT_DATE", "2026-09-28", 97),
                        new ExpenseOcrClient.Field("TOTAL", "12.40", 98),
                        new ExpenseOcrClient.Field("CURRENCY", "CAD", 99),
                        new ExpenseOcrClient.Field("VENDOR_ADDRESS", "Somewhere", 30)))), "next"));
        when(textract.page("job-1", "next")).thenReturn(new ExpenseOcrClient.Page("SUCCEEDED", List.of(), null));
        service.complete("alice", ID, KEY, "private-bucket", "job-1", "StartExpenseAnalysis", "SUCCEEDED");
        verify(textract).page("job-1", null);
        verify(textract).page("job-1", "next");
        verify(receipts).transitionOcr("alice", ID, KEY, ReceiptStatus.PROCESSING, "job-1",
                ReceiptStatus.READY, null,
                new OcrMetadata("Market", LocalDate.of(2026, 9, 28), new BigDecimal("12.40"), "CAD", false),
                NOW.plusSeconds(1));
    }

    @Test
    void ambiguousAndPartialResultsRequireReview() {
        when(receipts.find("alice", ID)).thenReturn(Optional.of(receipt(ReceiptStatus.PROCESSING, "job-1")));
        when(textract.page("job-1", null)).thenReturn(new ExpenseOcrClient.Page("PARTIAL_SUCCESS",
                List.of(new ExpenseOcrClient.Document(List.of(new ExpenseOcrClient.Field("TOTAL", "??", 50)))), null));
        service.complete("alice", ID, KEY, "private-bucket", "job-1", "StartExpenseAnalysis", "PARTIAL_SUCCESS");
        verify(receipts).transitionOcr(eq("alice"), eq(ID), eq(KEY), eq(ReceiptStatus.PROCESSING),
                eq("job-1"), eq(ReceiptStatus.REVIEW_NEEDED), isNull(),
                argThat(OcrMetadata::reviewRequired), eq(NOW.plusSeconds(1)));
    }

    @Test
    void terminalStartAndCompletionFailuresAreRecorded() {
        when(receipts.find("alice", ID)).thenReturn(Optional.of(receipt(ReceiptStatus.UPLOADED, null)));
        when(textract.start(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(BadDocumentException.builder().message("bad document").build());
        service.start("alice", ID);
        verify(receipts).transitionOcr("alice", ID, KEY, ReceiptStatus.UPLOADED, null,
                ReceiptStatus.OCR_FAILED, null, null, NOW.plusSeconds(1));
        clearInvocations(receipts);
        when(receipts.find("alice", ID)).thenReturn(Optional.of(receipt(ReceiptStatus.PROCESSING, "job-1")));
        service.complete("alice", ID, KEY, "private-bucket", "job-1", "StartExpenseAnalysis", "FAILED");
        verify(receipts).transitionOcr("alice", ID, KEY, ReceiptStatus.PROCESSING, "job-1",
                ReceiptStatus.OCR_FAILED, null, null, NOW.plusSeconds(1));
    }

    @Test
    void retryableFailuresPropagateWithoutMarkingTerminal() {
        when(receipts.find("alice", ID)).thenReturn(Optional.of(receipt(ReceiptStatus.UPLOADED, null)));
        RuntimeException transientFailure = new RuntimeException("throttled");
        when(textract.start(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(transientFailure);
        assertSame(transientFailure, assertThrows(RuntimeException.class, () -> service.start("alice", ID)));
        verify(receipts, never()).transitionOcr(anyString(), any(), anyString(), any(), any(), any(), any(), any(), any());
        when(receipts.find("alice", ID)).thenReturn(Optional.of(receipt(ReceiptStatus.PROCESSING, "job-1")));
        when(textract.page("job-1", null)).thenThrow(transientFailure);
        assertSame(transientFailure, assertThrows(RuntimeException.class,
                () -> service.complete("alice", ID, KEY, "private-bucket", "job-1", "StartExpenseAnalysis", "SUCCEEDED")));
        verify(receipts, never()).transitionOcr(anyString(), any(), anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void earlyCompletionRetriesUntilTheStartedJobIsCommitted() {
        when(receipts.find("alice", ID)).thenReturn(Optional.of(receipt(ReceiptStatus.UPLOADED, null)));
        assertThrows(IllegalStateException.class,
                () -> service.complete("alice", ID, KEY, "private-bucket", "job-1", "StartExpenseAnalysis", "SUCCEEDED"));
        verifyNoInteractions(textract);
        verify(receipts, never()).transitionOcr(anyString(), any(), anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void publicReceiptJsonOmitsInternalTextractJobId() throws Exception {
        String json = JsonMapper.builder().addModule(new JavaTimeModule()).build()
                .writeValueAsString(receipt(ReceiptStatus.PROCESSING, "job-secret"));
        assertFalse(json.contains("ocrJobId"));
        assertFalse(json.contains("job-secret"));
    }

    @Test
    void rejectsForeignOrNoncanonicalOriginalAndUnrelatedNotifications() {
        Receipt foreign = new Receipt(ID, "alice", "User merchant", LocalDate.of(2026, 9, 28),
                new BigDecimal("10.00"), "CAD", null, "bob/originals/" + ID,
                ReceiptStatus.UPLOADED, NOW, NOW);
        when(receipts.find("alice", ID)).thenReturn(Optional.of(foreign));
        service.start("alice", ID);
        verifyNoInteractions(textract);
        verify(receipts).transitionOcr("alice", ID, foreign.imageKey(), ReceiptStatus.UPLOADED,
                null, ReceiptStatus.OCR_FAILED, null, null, NOW.plusSeconds(1));
        clearInvocations(receipts);
        when(receipts.find("alice", ID)).thenReturn(Optional.of(receipt(ReceiptStatus.PROCESSING, "job-1")));
        service.complete("alice", ID, "bob/originals/" + ID, "private-bucket", "job-1", "StartExpenseAnalysis", "SUCCEEDED");
        service.complete("alice", ID, KEY, "other-bucket", "job-1", "StartExpenseAnalysis", "SUCCEEDED");
        service.complete("alice", ID, KEY, "private-bucket", "wrong-job", "StartExpenseAnalysis", "SUCCEEDED");
        verifyNoInteractions(textract);
        verify(receipts, never()).transitionOcr(anyString(), any(), anyString(), any(), any(), any(), any(), any(), any());
    }

    private static Receipt receipt(ReceiptStatus status, String jobId) {
        return new Receipt(ID, "alice", "User merchant", LocalDate.of(2026, 9, 28),
                new BigDecimal("10.00"), "CAD", null, KEY, status, NOW, NOW, jobId, null);
    }
}
