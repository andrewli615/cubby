package com.cubby.ocr;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OcrEventHandlerTest {
    private static final UUID ID = UUID.fromString("920a995d-693c-4634-9fcf-985b1ddc0199");
    private final ReceiptOcrService service = mock(ReceiptOcrService.class);

    @Test
    void streamStartsOnlyReceiptInserts() {
        var handler = new OcrStartHandler(service);
        Map<String, Object> keys = Map.of("PK", Map.of("S", "USER#alice"),
                "SK", Map.of("S", "RECEIPT#" + ID));
        Map<String, Object> invalidKeys = Map.of("PK", Map.of("S", "USER#bob"),
                "SK", Map.of("S", "RECEIPT#not-a-uuid"));
        handler.handleRequest(Map.of("Records", List.of(
                Map.of("eventName", "MODIFY", "dynamodb", Map.of("Keys", keys)),
                Map.of("eventName", "INSERT", "dynamodb", Map.of("Keys", keys)),
                Map.of("eventName", "INSERT", "dynamodb", Map.of("Keys", invalidKeys)))), null);
        verify(service).start("alice", ID);
        verifyNoMoreInteractions(service);
    }

    @Test
    void snsPassesOnlyParsedCompletionReferencesToService() {
        var handler = new OcrCompletionHandler(service);
        String valid = """
                {"JobId":"job-1","Status":"SUCCEEDED","API":"StartExpenseAnalysis",
                 "JobTag":"920a995d-693c-4634-9fcf-985b1ddc0199",
                 "DocumentLocation":{"S3Bucket":"private-bucket",
                   "S3ObjectName":"alice/originals/920a995d-693c-4634-9fcf-985b1ddc0199"}}
                """;
        handler.handleRequest(Map.of("Records", List.of(
                Map.of("Sns", Map.of("Message", "not-json")),
                Map.of("Sns", Map.of("Message", valid)))), null);
        verify(service).complete("alice", ID, "alice/originals/" + ID,
                "private-bucket", "job-1", "StartExpenseAnalysis", "SUCCEEDED");
        verifyNoMoreInteractions(service);
    }

    @Test
    void snsAndStreamPropagateRetryableServiceErrors() {
        var stream = new OcrStartHandler(service);
        doThrow(new IllegalStateException("retry")).when(service).start("alice", ID);
        assertThrows(IllegalStateException.class, () -> stream.handleRequest(Map.of("Records", List.of(
                Map.of("eventName", "INSERT", "dynamodb", Map.of("Keys", Map.of(
                        "PK", Map.of("S", "USER#alice"), "SK", Map.of("S", "RECEIPT#" + ID)))))), null));
    }
}
