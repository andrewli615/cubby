package com.cubby.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cubby.domain.Receipt;
import com.cubby.domain.ReceiptStatus;
import com.cubby.dto.CreateReceiptRequest;
import com.cubby.dto.UpdateReceiptRequest;
import com.cubby.dto.UploadUrlRequest;
import com.cubby.repository.ReceiptRepository;
import com.cubby.repository.ReceiptWriteConflictException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultReceiptServiceTest {
    private static final UUID ID = UUID.fromString("920a995d-693c-4634-9fcf-985b1ddc0199");
    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 9, 28);
    private final ReceiptRepository repository = mock(ReceiptRepository.class);
    private final ReceiptUploads uploads = mock(ReceiptUploads.class);
    private final ReceiptService service = new DefaultReceiptService(repository, uploads,
            Clock.fixed(NOW, ZoneOffset.UTC), () -> ID);

    @Test
    void createAssignsIdentityStatusAndTimestampsThenDelegates() {
        when(repository.save(eq("alice"), any())).thenAnswer(invocation -> invocation.getArgument(1));
        var created = service.create("alice", create("alice/image"));
        assertEquals(ID, created.receiptId());
        assertEquals("alice", created.userId());
        assertEquals(ReceiptStatus.UPLOADED, created.status());
        assertEquals(NOW, created.createdAt());
        assertEquals(NOW, created.updatedAt());
        assertEquals(new BigDecimal("19.99"), created.total());
        verify(repository).save("alice", created);
        verifyNoMoreInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bob/image", "alice-other/image", "image", "alice/", "alice/ ", "alice/../bob/image", "alice/%2e%2e/bob", "alice//image"})
    void rejectsImageKeysOutsideTheUsersNamespace(String key) {
        assertThrows(IllegalArgumentException.class, () -> service.create("alice", create(key)));
        verifyNoInteractions(repository);
    }

    @Test
    void updateChangesMetadataWhilePreservingIdentityOriginalImageStatusAndCreationTime() {
        Receipt existing = receipt("alice", NOW.minusSeconds(10));
        when(repository.find("alice", ID)).thenReturn(Optional.of(existing));
        when(repository.update(eq("alice"), any())).thenAnswer(invocation -> invocation.getArgument(1));
        var result = service.update("alice", ID, update());
        assertEquals(ID, result.receiptId());
        assertEquals("alice", result.userId());
        assertEquals("alice/image", result.imageKey());
        assertEquals(ReceiptStatus.READY, result.status());
        assertEquals(existing.createdAt(), result.createdAt());
        assertEquals(NOW, result.updatedAt());
        assertEquals("Changed", result.merchant());
        assertEquals(DATE.minusDays(1), result.purchaseDate());
        assertEquals(new BigDecimal("25.00"), result.total());
        assertEquals("USD", result.currency());
        assertNull(result.category());
        verify(repository).update("alice", result);
    }

    @Test
    void updateDoesNotMoveTimestampBackwardsIfClockIsBehind() {
        when(repository.find("alice", ID)).thenReturn(Optional.of(receipt("alice", NOW.plusSeconds(10))));
        when(repository.update(eq("alice"), any())).thenAnswer(invocation -> invocation.getArgument(1));
        assertEquals(NOW.plusSeconds(10), service.update("alice", ID, update()).updatedAt());
    }

    @Test
    void editKeepsOcrJobAndExtractedMetadataSeparateFromEnteredValues() {
        Receipt previous = receipt("alice", NOW.minusSeconds(10));
        var extracted = new com.cubby.domain.OcrMetadata("OCR vendor", null, new BigDecimal("12.40"), "CAD", true);
        Receipt reviewing = new Receipt(previous.receiptId(), previous.userId(), previous.merchant(),
                previous.purchaseDate(), previous.total(), previous.currency(), previous.category(),
                previous.imageKey(), ReceiptStatus.REVIEW_NEEDED, previous.createdAt(), previous.updatedAt(),
                "job-1", extracted);
        when(repository.find("alice", ID)).thenReturn(Optional.of(reviewing));
        when(repository.update(eq("alice"), any())).thenAnswer(invocation -> invocation.getArgument(1));
        Receipt result = service.update("alice", ID, update());
        assertEquals(ReceiptStatus.REVIEW_NEEDED, result.status());
        assertEquals("job-1", result.ocrJobId());
        assertEquals(extracted, result.ocr());
        assertEquals("Changed", result.merchant());
    }

    @Test
    void missingUpdateDoesNotWriteAndMissingGetIsEmpty() {
        when(repository.find("alice", ID)).thenReturn(Optional.empty());
        assertTrue(service.get("alice", ID).isEmpty());
        assertThrows(ReceiptNotFoundException.class, () -> service.update("alice", ID, update()));
        verify(repository, never()).update(anyString(), any());
        verify(repository, never()).save(anyString(), any());
    }

    @Test
    void getAndListScopeRepositoryCallsAndReturnSnapshots() {
        Receipt receipt = receipt("alice", NOW);
        when(repository.find("alice", ID)).thenReturn(Optional.of(receipt));
        when(repository.listByUser("alice")).thenReturn(List.of(receipt));
        assertEquals(Optional.of(receipt), service.get("alice", ID));
        assertEquals(List.of(receipt), service.list("alice"));
        verify(repository).find("alice", ID);
        verify(repository).listByUser("alice");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void refusesForeignOrMismatchedRecordsFromRepository() {
        when(repository.find("alice", ID)).thenReturn(Optional.of(receipt("bob", NOW)));
        when(repository.listByUser("alice")).thenReturn(List.of(receipt("bob", NOW)));
        assertThrows(IllegalStateException.class, () -> service.get("alice", ID));
        assertThrows(IllegalStateException.class, () -> service.list("alice"));
        UUID otherId = UUID.randomUUID();
        when(repository.find("alice", otherId)).thenReturn(Optional.of(receipt("alice", NOW)));
        assertThrows(IllegalStateException.class, () -> service.get("alice", otherId));
    }

    @Test
    void deleteIsScopedAndReportsMissingRecords() {
        when(repository.delete("alice", ID)).thenReturn(true);
        when(repository.delete("bob", ID)).thenReturn(false);
        service.delete("alice", ID);
        assertThrows(ReceiptNotFoundException.class, () -> service.delete("bob", ID));
        verify(repository).delete("alice", ID);
        verify(repository).delete("bob", ID);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void mapsRepositoryConflictsForCreateAndUpdateWithoutRetrying() {
        var failure = new ReceiptWriteConflictException("condition failed", new RuntimeException());
        when(repository.save(eq("alice"), any())).thenThrow(failure);
        when(repository.find("alice", ID)).thenReturn(Optional.of(receipt("alice", NOW)));
        when(repository.update(eq("alice"), any())).thenThrow(failure);
        assertSame(failure, assertThrows(ReceiptConflictException.class,
                () -> service.create("alice", create("alice/image"))).getCause());
        assertSame(failure, assertThrows(ReceiptConflictException.class,
                () -> service.update("alice", ID, update())).getCause());
        verify(repository).save(eq("alice"), any());
        verify(repository).update(eq("alice"), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingIdentityBeforeRepositoryCalls(String user) {
        assertThrows(IllegalArgumentException.class, () -> service.create(user, create("alice/image")));
        assertThrows(IllegalArgumentException.class, () -> service.get(user, ID));
        assertThrows(IllegalArgumentException.class, () -> service.list(user));
        assertThrows(IllegalArgumentException.class, () -> service.update(user, ID, update()));
        assertThrows(IllegalArgumentException.class, () -> service.delete(user, ID));
        verifyNoInteractions(repository);
    }

    @Test
    void validatesRequiredParametersBeforeRepositoryCalls() {
        assertThrows(IllegalArgumentException.class, () -> service.create("alice", null));
        assertThrows(IllegalArgumentException.class, () -> service.update("alice", ID, null));
        assertThrows(IllegalArgumentException.class, () -> service.get("alice", null));
        assertThrows(IllegalArgumentException.class, () -> service.update("alice", null, update()));
        assertThrows(IllegalArgumentException.class, () -> service.delete("alice", null));
        verifyNoInteractions(repository);
    }

    @Test
    void uploadSigningDelegatesVerifiedOwnerAndMetadataWithoutPersistence() {
        var request = new UploadUrlRequest("receipt.png", "image/png", 100L);
        service.createUploadUrl("alice", request);
        verify(uploads).createUploadUrl("alice", request);
        verifyNoInteractions(repository);
        assertThrows(IllegalArgumentException.class, () -> service.createUploadUrl("alice/bob", request));
        assertThrows(IllegalArgumentException.class, () -> service.createUploadUrl("alice", null));
        verifyNoMoreInteractions(uploads);
    }

    private static CreateReceiptRequest create(String imageKey) {
        return new CreateReceiptRequest("Store", DATE, new BigDecimal("19.99"), "CAD", "Office", imageKey);
    }

    private static UpdateReceiptRequest update() {
        return new UpdateReceiptRequest("Changed", DATE.minusDays(1), new BigDecimal("25.00"), "USD", null);
    }

    private static Receipt receipt(String user, Instant updatedAt) {
        return new Receipt(ID, user, "Store", DATE, new BigDecimal("19.99"), "CAD", "Office",
                user + "/image", ReceiptStatus.READY, NOW.minusSeconds(30), updatedAt);
    }
}
