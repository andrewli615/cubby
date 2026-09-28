package com.cubby.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cubby.domain.Receipt;
import com.cubby.repository.ReceiptRepository;
import com.cubby.service.DefaultReceiptService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Runs the real HTTP and business layers against an in-memory mocked persistence boundary. */
class ReceiptApiLocalTest {
    private static final UUID ID = UUID.fromString("920a995d-693c-4634-9fcf-985b1ddc0199");
    private static final String PATH = "/receipts/" + ID;
    private static final String CREATE = """
            {"merchant":"Store","purchaseDate":"2026-09-28","total":19.99,"currency":"CAD",
             "category":"Office","imageKey":"alice/image"}
            """;
    private static final String UPDATE = """
            {"merchant":"Changed","purchaseDate":"2026-09-27","total":25.50,"currency":"CAD","category":null}
            """;
    private final Map<String, Receipt> records = new HashMap<>();
    private final ReceiptRepository repository = mock(ReceiptRepository.class);
    private final DefaultReceiptService service = new DefaultReceiptService(repository, mock(com.cubby.service.ReceiptUploads.class),
            Clock.fixed(Instant.parse("2026-09-28T12:00:00Z"), ZoneOffset.UTC), () -> ID);

    ReceiptApiLocalTest() {
        when(repository.save(anyString(), any())).thenAnswer(invocation -> {
            Receipt receipt = invocation.getArgument(1);
            records.put(key(invocation.getArgument(0), receipt.receiptId()), receipt);
            return receipt;
        });
        when(repository.find(anyString(), any())).thenAnswer(invocation ->
                Optional.ofNullable(records.get(key(invocation.getArgument(0), invocation.getArgument(1)))));
        when(repository.listByUser(anyString())).thenAnswer(invocation -> records.values().stream()
                .filter(receipt -> receipt.userId().equals(invocation.getArgument(0))).toList());
        when(repository.update(anyString(), any())).thenAnswer(invocation -> {
            Receipt receipt = invocation.getArgument(1);
            records.put(key(invocation.getArgument(0), receipt.receiptId()), receipt);
            return receipt;
        });
        when(repository.delete(anyString(), any())).thenAnswer(invocation ->
                records.remove(key(invocation.getArgument(0), invocation.getArgument(1))) != null);
    }

    @Test
    void completeCrudLifecycleUsesRealHandlerAndServiceWithoutAws() throws Exception {
        var handler = handler("alice");
        var created = invoke(handler, "POST", "/receipts", CREATE);
        assertEquals(201, created.get("statusCode"));
        JsonNode initial = json(created);
        assertEquals("alice", initial.get("userId").asText());
        assertEquals("UPLOADED", initial.get("status").asText());

        var list = invoke(handler, "GET", "/receipts", null);
        assertEquals(200, list.get("statusCode"));
        assertEquals(1, json(list).size());
        assertEquals(initial, json(invoke(handler, "GET", PATH, null)));

        var update = invoke(handler, "PUT", PATH, UPDATE);
        assertEquals(200, update.get("statusCode"));
        JsonNode changed = json(update);
        assertEquals("Changed", changed.get("merchant").asText());
        assertTrue(changed.get("category").isNull());
        for (String field : new String[] {"receiptId", "userId", "createdAt", "imageKey", "status"}) {
            assertEquals(initial.get(field), changed.get(field));
        }

        assertEquals(204, invoke(handler, "DELETE", PATH, null).get("statusCode"));
        assertEquals(404, invoke(handler, "GET", PATH, null).get("statusCode"));
        assertEquals(404, invoke(handler, "PUT", PATH, UPDATE).get("statusCode"));
        assertEquals(404, invoke(handler, "DELETE", PATH, null).get("statusCode"));
        assertEquals(0, json(invoke(handler, "GET", "/receipts", null)).size());
    }

    @Test
    void anotherUserCannotReadUpdateDeleteOrReuseTheOriginalImage() throws Exception {
        var alice = handler("alice");
        var bob = handler("bob");
        assertEquals(201, invoke(alice, "POST", "/receipts", CREATE).get("statusCode"));
        assertEquals(0, json(invoke(bob, "GET", "/receipts", null)).size());
        assertEquals(404, invoke(bob, "GET", PATH, null).get("statusCode"));
        assertEquals(404, invoke(bob, "PUT", PATH, UPDATE).get("statusCode"));
        assertEquals(404, invoke(bob, "DELETE", PATH, null).get("statusCode"));
        assertEquals(400, invoke(bob, "POST", "/receipts", CREATE).get("statusCode"));
        assertEquals("Store", json(invoke(alice, "GET", PATH, null)).get("merchant").asText());
        verify(repository, never()).save(eq("bob"), any());
        verify(repository, never()).update(eq("bob"), any());
    }

    private ReceiptApiHandler handler(String user) {
        return new ReceiptApiHandler(service, ignored -> Optional.of(user));
    }

    private static Map<String, Object> invoke(ReceiptApiHandler handler, String method, String path, String body) {
        return handler.handleRequest(ReceiptApiHandlerTest.event(method, path, body), null);
    }

    private static JsonNode json(Map<String, Object> response) throws Exception {
        return new ObjectMapper().readTree((String) response.get("body"));
    }

    private static String key(String user, UUID id) {
        return user + "/" + id;
    }
}
