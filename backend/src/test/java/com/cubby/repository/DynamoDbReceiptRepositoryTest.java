package com.cubby.repository;

import static com.cubby.repository.ReceiptFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cubby.domain.ReceiptStatus;
import com.cubby.domain.OcrMetadata;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

class DynamoDbReceiptRepositoryTest {
    private final DynamoDbClient client = mock(DynamoDbClient.class);
    private final ReceiptRepository repository = new DynamoDbReceiptRepository(client, "receipts-test");

    @Test
    void saveInsertsExactSnapshotWithoutOverwritingAnExistingReceipt() {
        var receipt = receipt("alice", "Office", ReceiptStatus.READY);
        assertEquals(receipt, repository.save("alice", receipt));
        var request = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(client).putItem(request.capture());
        assertEquals("receipts-test", request.getValue().tableName());
        assertEquals(item("alice"), request.getValue().item());
        assertEquals("attribute_not_exists(#pk) AND attribute_not_exists(#sk)",
                request.getValue().conditionExpression());
        assertEquals(Map.of("#pk", "PK", "#sk", "SK"), request.getValue().expressionAttributeNames());
        verifyNoMoreInteractions(client);
    }

    @Test
    void saveReportsDuplicateWithoutRetryingUnconditionally() {
        var failure = ConditionalCheckFailedException.builder().message("duplicate").build();
        when(client.putItem(any(PutItemRequest.class))).thenThrow(failure);
        var conflict = assertThrows(ReceiptWriteConflictException.class,
                () -> repository.save("alice", receipt("alice", null, ReceiptStatus.UPLOADED)));
        assertSame(failure, conflict.getCause());
        verify(client).putItem(any(PutItemRequest.class));
        verifyNoMoreInteractions(client);
    }

    @Test
    void findUsesBothOwnerAndIdAndReturnsTheSnapshot() {
        when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().item(item("alice")).build());
        assertEquals(receipt("alice", "Office", ReceiptStatus.READY), repository.find("alice", ID).orElseThrow());
        var request = ArgumentCaptor.forClass(GetItemRequest.class);
        verify(client).getItem(request.capture());
        assertEquals(key("alice"), request.getValue().key());
        assertEquals("receipts-test", request.getValue().tableName());
        assertTrue(request.getValue().consistentRead());
    }

    @Test
    void missingReadReturnsEmptyAndNeverSearchesAnotherPartition() {
        when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().build());
        assertTrue(repository.find("bob", ID).isEmpty());
        verify(client).getItem(GetItemRequest.builder().tableName("receipts-test").key(key("bob"))
                .consistentRead(true).build());
        verifyNoMoreInteractions(client);
    }

    @Test
    void refusesToReturnAnotherUsersReceiptOrAnUnexpectedId() {
        when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().item(item("bob")).build());
        assertThrows(IllegalStateException.class, () -> repository.find("alice", ID));
        when(client.getItem(any(GetItemRequest.class))).thenReturn(GetItemResponse.builder().item(item("alice")).build());
        assertThrows(IllegalStateException.class, () -> repository.find("alice", UUID.randomUUID()));
    }

    @Test
    void listFollowsPaginationIncludingEmptyPagesWithinTheSameUserPartition() {
        var pageKey = key("alice");
        when(client.query(any(QueryRequest.class))).thenReturn(
                QueryResponse.builder().lastEvaluatedKey(pageKey).build(),
                QueryResponse.builder().items(List.of(item("alice"))).build());
        assertEquals(List.of(receipt("alice", "Office", ReceiptStatus.READY)), repository.listByUser("alice"));
        var requests = ArgumentCaptor.forClass(QueryRequest.class);
        verify(client, times(2)).query(requests.capture());
        assertTrue(requests.getAllValues().get(0).exclusiveStartKey().isEmpty());
        assertEquals(pageKey, requests.getAllValues().get(1).exclusiveStartKey());
        for (var request : requests.getAllValues()) {
            assertEquals("receipts-test", request.tableName());
            assertEquals("#pk = :user AND begins_with(#sk, :receipt)", request.keyConditionExpression());
            assertEquals(Map.of("#pk", "PK", "#sk", "SK"), request.expressionAttributeNames());
            assertEquals(Map.of(":user", string("USER#alice"), ":receipt", string("RECEIPT#")),
                    request.expressionAttributeValues());
            assertNull(request.filterExpression());
        }
        verifyNoMoreInteractions(client);
    }

    @Test
    void listReturnsEmptyWhenNoReceiptsExist() {
        when(client.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder().build());
        assertEquals(List.of(), repository.listByUser("alice"));
        verify(client).query(any(QueryRequest.class));
        verifyNoMoreInteractions(client);
    }

    @Test
    void listRejectsUnexpectedForeignItems() {
        when(client.query(any(QueryRequest.class))).thenReturn(QueryResponse.builder().items(List.of(item("bob"))).build());
        assertThrows(IllegalStateException.class, () -> repository.listByUser("alice"));
    }

    @Test
    void updateReplacesSnapshotAndClearsCategoryWithAtomicImmutableFieldGuards() {
        var receipt = receipt("alice", null, ReceiptStatus.OCR_FAILED);
        assertEquals(receipt, repository.update("alice", receipt));
        var request = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(client).putItem(request.capture());
        var expected = item("alice");
        expected.remove("category");
        expected.put("status", string("OCR_FAILED"));
        assertEquals(expected, request.getValue().item());
        assertEquals("receipts-test", request.getValue().tableName());
        assertEquals("attribute_exists(#pk) AND attribute_exists(#sk)"
                + " AND #created = :created AND #image = :image"
                + " AND #status = :status AND attribute_not_exists(#job)", request.getValue().conditionExpression());
        assertEquals(Map.of("#pk", "PK", "#sk", "SK", "#created", "createdAt", "#image", "imageKey",
                "#status", "status", "#job", "ocrJobId"),
                request.getValue().expressionAttributeNames());
        assertEquals(Map.of(":created", string(CREATED.toString()), ":image", string("alice/image"),
                ":status", string("OCR_FAILED")),
                request.getValue().expressionAttributeValues());
        verifyNoMoreInteractions(client);
    }

    @Test
    void updateReportsMissingOrChangedRecordWithoutCreatingIt() {
        var failure = ConditionalCheckFailedException.builder().message("condition failed").build();
        when(client.putItem(any(PutItemRequest.class))).thenThrow(failure);
        var conflict = assertThrows(ReceiptWriteConflictException.class,
                () -> repository.update("alice", receipt("alice", null, ReceiptStatus.READY)));
        assertSame(failure, conflict.getCause());
        verify(client).putItem(any(PutItemRequest.class));
        verifyNoMoreInteractions(client);
    }

    @Test
    void ocrTransitionConditionGuardsOwnerImageStatusAndJobWithoutReplacingUserMetadata() {
        var metadata = new OcrMetadata("Market", LocalDate.of(2026, 9, 28),
                new BigDecimal("12.40"), "CAD", false);
        assertTrue(repository.transitionOcr("alice", ID, "alice/originals/" + ID,
                ReceiptStatus.PROCESSING, "job-1", ReceiptStatus.READY, null, metadata, CREATED.plusSeconds(2)));
        var request = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(client).updateItem(request.capture());
        var update = request.getValue();
        assertEquals(key("alice"), update.key());
        assertEquals("attribute_exists(#pk) AND attribute_exists(#sk)"
                + " AND #image = :image AND #status = :from AND #job = :expectedJob",
                update.conditionExpression());
        assertEquals("SET #status = :to, #updated = :updated, #ocr = :ocr", update.updateExpression());
        assertEquals(string("job-1"), update.expressionAttributeValues().get(":expectedJob"));
        assertEquals(string("READY"), update.expressionAttributeValues().get(":to"));
        assertFalse(update.expressionAttributeValues().containsKey(":job"));
        assertEquals(string("Market"), update.expressionAttributeValues().get(":ocr").m().get("merchant"));
        assertFalse(update.updateExpression().contains("merchant"));
    }

    @Test
    void duplicateOcrTransitionIsAConditionalNoOp() {
        when(client.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(ConditionalCheckFailedException.builder().message("duplicate").build());
        assertFalse(repository.transitionOcr("alice", ID, "alice/originals/" + ID,
                ReceiptStatus.UPLOADED, null, ReceiptStatus.PROCESSING, "job-1", null, CREATED.plusSeconds(2)));
        var request = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(client).updateItem(request.capture());
        assertTrue(request.getValue().conditionExpression().contains("attribute_not_exists(#job)"));
        assertEquals("SET #status = :to, #updated = :updated, #job = :job", request.getValue().updateExpression());
    }

    @Test
    void deleteUsesOnlyTheRequestedUsersKeyAndReportsWhetherItExisted() {
        when(client.deleteItem(any(DeleteItemRequest.class))).thenReturn(
                DeleteItemResponse.builder().attributes(item("alice")).build(),
                DeleteItemResponse.builder().build());
        assertTrue(repository.delete("alice", ID));
        assertFalse(repository.delete("bob", ID));
        var requests = ArgumentCaptor.forClass(DeleteItemRequest.class);
        verify(client, times(2)).deleteItem(requests.capture());
        assertEquals(key("alice"), requests.getAllValues().get(0).key());
        assertEquals(key("bob"), requests.getAllValues().get(1).key());
        for (var request : requests.getAllValues()) {
            assertEquals("receipts-test", request.tableName());
            assertEquals(ReturnValue.ALL_OLD, request.returnValues());
        }
        verifyNoMoreInteractions(client);
    }

    @Test
    void rejectsCrossUserWritesBeforeCallingTheSdk() {
        var foreignReceipt = receipt("bob", null, ReceiptStatus.READY);
        assertThrows(IllegalArgumentException.class, () -> repository.save("alice", foreignReceipt));
        assertThrows(IllegalArgumentException.class, () -> repository.update("alice", foreignReceipt));
        verifyNoInteractions(client);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void everyOperationRejectsMissingUserBeforeCallingTheSdk(String userId) {
        var receipt = receipt("alice", null, ReceiptStatus.READY);
        assertThrows(IllegalArgumentException.class, () -> repository.save(userId, receipt));
        assertThrows(IllegalArgumentException.class, () -> repository.find(userId, ID));
        assertThrows(IllegalArgumentException.class, () -> repository.listByUser(userId));
        assertThrows(IllegalArgumentException.class, () -> repository.update(userId, receipt));
        assertThrows(IllegalArgumentException.class, () -> repository.delete(userId, ID));
        verifyNoInteractions(client);
    }

    @Test
    void rejectsMissingIdentifiersAndSnapshotsBeforeCallingTheSdk() {
        assertThrows(IllegalArgumentException.class, () -> repository.find("alice", null));
        assertThrows(IllegalArgumentException.class, () -> repository.delete("alice", null));
        assertThrows(IllegalArgumentException.class, () -> repository.save("alice", null));
        assertThrows(IllegalArgumentException.class, () -> repository.update("alice", null));
        verifyNoInteractions(client);
    }

    @Test
    void requiresClientAndTableNameWithoutMakingSdkCalls() {
        assertThrows(IllegalArgumentException.class, () -> new DynamoDbReceiptRepository(null, "receipts"));
        assertThrows(IllegalArgumentException.class, () -> new DynamoDbReceiptRepository(client, null));
        assertThrows(IllegalArgumentException.class, () -> new DynamoDbReceiptRepository(client, " "));
        verifyNoInteractions(client);
    }

    @Test
    void doesNotHideSdkFailuresAsMissingRecordsOrSuccessfulWrites() {
        var failure = DynamoDbException.builder().message("service unavailable").build();
        when(client.getItem(any(GetItemRequest.class))).thenThrow(failure);
        when(client.query(any(QueryRequest.class))).thenThrow(failure);
        when(client.putItem(any(PutItemRequest.class))).thenThrow(failure);
        when(client.deleteItem(any(DeleteItemRequest.class))).thenThrow(failure);
        var receipt = receipt("alice", null, ReceiptStatus.READY);
        assertSame(failure, assertThrows(DynamoDbException.class, () -> repository.find("alice", ID)));
        assertSame(failure, assertThrows(DynamoDbException.class, () -> repository.listByUser("alice")));
        assertSame(failure, assertThrows(DynamoDbException.class, () -> repository.save("alice", receipt)));
        assertSame(failure, assertThrows(DynamoDbException.class, () -> repository.update("alice", receipt)));
        assertSame(failure, assertThrows(DynamoDbException.class, () -> repository.delete("alice", ID)));
    }

    private static Map<String, AttributeValue> key(String userId) {
        return Map.of("PK", string("USER#" + userId), "SK", string("RECEIPT#" + ID));
    }
}
