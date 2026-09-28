package com.cubby.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cubby.auth.IdentityProvider;
import com.cubby.domain.Receipt;
import com.cubby.domain.ReceiptStatus;
import com.cubby.dto.CreateReceiptRequest;
import com.cubby.dto.UpdateReceiptRequest;
import com.cubby.service.ReceiptConflictException;
import com.cubby.service.ReceiptListQuery;
import com.cubby.service.ReceiptNotFoundException;
import com.cubby.service.ReceiptService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class ReceiptApiHandlerTest {
    private static final UUID ID = UUID.fromString("920a995d-693c-4634-9fcf-985b1ddc0199");
    private static final String ITEM_PATH = "/receipts/" + ID;
    private static final String METADATA = """
            {"merchant":"Store","purchaseDate":"2026-09-28","total":12345678901234567890.123456789,
             "currency":"CAD","category":"Office"}
            """.trim();
    private static final String CREATE = METADATA.substring(0, METADATA.length() - 1) + ",\"imageKey\":\"alice/image\"}";
    private final ReceiptService service = mock(ReceiptService.class);
    private final IdentityProvider identities = mock(IdentityProvider.class);
    private final ReceiptApiHandler handler = new ReceiptApiHandler(service, identities);

    ReceiptApiHandlerTest() {
        when(identities.authenticatedUserId(anyMap())).thenReturn(Optional.of("alice"));
    }

    @Test
    void healthIsPublicAndDoesNotResolveIdentityOrCallReceiptService() throws Exception {
        var response = call("GET", "/health", null);
        assertEquals(200, response.get("statusCode"));
        assertEquals("ok", json(response).get("status").asText());
        verifyNoInteractions(service, identities);
    }

    @Test
    void createMapsValidatedDtoAndSerializesExactReceiptValues() throws Exception {
        when(service.create(eq("alice"), any())).thenReturn(receipt());
        var response = call("POST", "/receipts", CREATE);
        assertEquals(201, response.get("statusCode"));
        assertEquals(ITEM_PATH, headers(response).get("location"));
        var body = json(response);
        assertEquals(ID.toString(), body.get("receiptId").asText());
        assertEquals("alice", body.get("userId").asText());
        assertEquals("2026-09-28", body.get("purchaseDate").asText());
        assertEquals("2026-09-28T12:00:00Z", body.get("createdAt").asText());
        assertEquals("UPLOADED", body.get("status").asText());
        assertTrue(((String) response.get("body")).contains("12345678901234567890.123456789"));
        var request = ArgumentCaptor.forClass(CreateReceiptRequest.class);
        verify(service).create(eq("alice"), request.capture());
        assertEquals(new BigDecimal("12345678901234567890.123456789"), request.getValue().total());
        assertEquals("Store", request.getValue().merchant());
        verifyNoMoreInteractions(service);
    }

    @Test
    void listReturnsJsonArrayAndUsesOnlyProviderIdentity() throws Exception {
        when(service.list(eq("alice"), any(ReceiptListQuery.class))).thenReturn(List.of(receipt()));
        var event = event("GET", "/receipts", null);
        event.put("queryStringParameters", Map.of("merchant", "Store"));
        event.put("rawQueryString", "merchant=Store&sort=date_desc");
        event.put("headers", Map.of("x-user-id", "bob", "authorization", "unverified"));
        event.put("userId", "bob");
        var response = handler.handleRequest(event, null);
        assertEquals(200, response.get("statusCode"));
        assertTrue(json(response).isArray());
        assertEquals("alice", json(response).get(0).get("userId").asText());
        verify(service).list("alice", ReceiptListQuery.parse("merchant=Store&sort=date_desc"));
        ArgumentCaptor<Map<String, Object>> context = ArgumentCaptor.captor();
        verify(identities).authenticatedUserId(context.capture());
        assertEquals(event.get("requestContext"), context.getValue());
        assertFalse(context.getValue().containsKey("headers"));
        verifyNoMoreInteractions(service);
    }

    @Test
    void rejectsUnknownDuplicateAndInvalidListQueriesBeforeServiceCall() {
        for (String raw : List.of("userId=bob", "dateFrom=2026-02-30",
                "dateFrom=2026-10-01&dateTo=2026-09-01", "sort=unknown", "merchant=a&merchant=b")) {
            var event = event("GET", "/receipts", null);
            event.put("rawQueryString", raw);
            assertError(400, "INVALID_REQUEST", handler.handleRequest(event, null));
        }
        verifyNoInteractions(service);
    }

    @Test
    void getUsesPathUuidAndProviderIdentity() throws Exception {
        when(service.get("alice", ID)).thenReturn(Optional.of(receipt()));
        var event = event("GET", ITEM_PATH, null);
        event.put("pathParameters", Map.of("receiptId", UUID.randomUUID().toString(), "userId", "bob"));
        var response = handler.handleRequest(event, null);
        assertEquals(200, response.get("statusCode"));
        assertEquals(ID.toString(), json(response).get("receiptId").asText());
        verify(service).get("alice", ID);
        verifyNoMoreInteractions(service);
    }

    @Test
    void putDelegatesFullMetadataReplacement() {
        when(service.update(eq("alice"), eq(ID), any())).thenReturn(receipt());
        assertEquals(200, call("PUT", ITEM_PATH, METADATA).get("statusCode"));
        var request = ArgumentCaptor.forClass(UpdateReceiptRequest.class);
        verify(service).update(eq("alice"), eq(ID), request.capture());
        assertEquals("Office", request.getValue().category());
        assertEquals(LocalDate.of(2026, 9, 28), request.getValue().purchaseDate());
        verifyNoMoreInteractions(service);
    }

    @Test
    void deleteReturnsEmpty204AndDelegatesToService() {
        var response = call("DELETE", ITEM_PATH, null);
        assertEquals(204, response.get("statusCode"));
        assertEquals("", response.get("body"));
        verify(service).delete("alice", ID);
        verifyNoMoreInteractions(service);
    }

    @Test
    void decodesBase64JsonBeforeDelegation() {
        when(service.create(eq("alice"), any())).thenReturn(receipt());
        var event = event("POST", "/receipts", Base64.getEncoder().encodeToString(CREATE.getBytes(StandardCharsets.UTF_8)));
        event.put("isBase64Encoded", true);
        assertEquals(201, handler.handleRequest(event, null).get("statusCode"));
        verify(service).create(eq("alice"), any(CreateReceiptRequest.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "null", "[]", "{", "{}", "{} {}",
            "{\"merchant\":123}", "{\"total\":-1}", "{\"total\":1,\"total\":2}"})
    void rejectsMalformedOrIncompleteJsonWithoutCallingService(String body) {
        assertError(400, "INVALID_REQUEST", call("POST", "/receipts", body));
        assertError(400, "INVALID_REQUEST", call("PUT", ITEM_PATH, body));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidBase64AndNonBooleanEncodingFlags() {
        var event = event("POST", "/receipts", "%%%");
        event.put("isBase64Encoded", true);
        assertError(400, "INVALID_REQUEST", handler.handleRequest(event, null));
        event.put("isBase64Encoded", "true");
        assertError(400, "INVALID_REQUEST", handler.handleRequest(event, null));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"receiptId", "userId", "createdAt", "updatedAt", "status", "unexpected"})
    void rejectsServerManagedOrUnknownBodyFields(String field) {
        String create = CREATE.substring(0, CREATE.length() - 1) + ",\"" + field + "\":\"spoofed\"}";
        String update = METADATA.substring(0, METADATA.length() - 1) + ",\"" + field + "\":\"spoofed\"}";
        assertError(400, "INVALID_REQUEST", call("POST", "/receipts", create));
        assertError(400, "INVALID_REQUEST", call("PUT", ITEM_PATH, update));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsImageChangesAndInvalidDomainValues() {
        assertError(400, "INVALID_REQUEST", call("PUT", ITEM_PATH, CREATE));
        for (String body : List.of(
                CREATE.replace("12345678901234567890.123456789", "-0.01"),
                CREATE.replace("12345678901234567890.123456789", "\"19.99\""),
                CREATE.replace("\"Store\"", "\" \""),
                CREATE.replace("\"Store\"", "123"),
                CREATE.replace("\"Store\"", "true"),
                CREATE.replace("\"category\":\"Office\"", "\"category\":12.5"),
                CREATE.replace("2026-09-28", "2026-02-30"),
                CREATE.replace("CAD", "invalid"),
                CREATE.replace("\"category\":\"Office\"", "\"category\":\" \""),
                CREATE.replace("\"merchant\":\"Store\"", "\"merchant\":\"Store\",\"merchant\":\"Other\""),
                CREATE + " {}")) {
            assertError(400, "INVALID_REQUEST", call("POST", "/receipts", body));
        }
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1"})
    void rejectsInvalidOrNoncanonicalUuid(String id) {
        for (String method : List.of("GET", "PUT", "DELETE")) {
            assertError(400, "INVALID_REQUEST", call(method, "/receipts/" + id, METADATA));
        }
        verifyNoInteractions(service);
    }

    @Test
    void missingRecordsMapTo404ForGetUpdateAndDelete() {
        when(service.get("alice", ID)).thenReturn(Optional.empty());
        when(service.update(eq("alice"), eq(ID), any())).thenThrow(new ReceiptNotFoundException());
        doThrow(new ReceiptNotFoundException()).when(service).delete("alice", ID);
        assertError(404, "NOT_FOUND", call("GET", ITEM_PATH, null));
        assertError(404, "NOT_FOUND", call("PUT", ITEM_PATH, METADATA));
        assertError(404, "NOT_FOUND", call("DELETE", ITEM_PATH, null));
    }

    @Test
    void anonymousRequestsCannotUseAnyCrudRouteEvenWithSpoofedIdentity() {
        when(identities.authenticatedUserId(anyMap())).thenReturn(Optional.empty());
        for (String route : List.of("POST /receipts", "GET /receipts", "GET " + ITEM_PATH,
                "PUT " + ITEM_PATH, "DELETE " + ITEM_PATH)) {
            String[] parts = route.split(" ");
            var event = event(parts[0], parts[1], CREATE);
            event.put("headers", Map.of("x-user-id", "alice"));
            event.put("queryStringParameters", Map.of("userId", "alice"));
            assertError(401, "UNAUTHORIZED", handler.handleRequest(event, null));
        }
        verifyNoInteractions(service);
    }

    @Test
    void blankIdentityIsRejectedAndProviderFailureDoesNotFallBackToRequestData() {
        when(identities.authenticatedUserId(anyMap())).thenReturn(Optional.of(" "));
        assertError(401, "UNAUTHORIZED", call("GET", "/receipts", null));
        when(identities.authenticatedUserId(anyMap())).thenThrow(new IllegalStateException("private details"));
        assertError(500, "INTERNAL_ERROR", call("GET", "/receipts", null));
        verifyNoInteractions(service);
    }

    @Test
    void mapsConflictAndSanitizesUnexpectedErrors() {
        when(service.create(eq("alice"), any())).thenThrow(new ReceiptConflictException(new RuntimeException("private")));
        assertError(409, "CONFLICT", call("POST", "/receipts", CREATE));
        when(service.list("alice")).thenThrow(new IllegalStateException("private SDK diagnostics"));
        var response = call("GET", "/receipts", null);
        assertError(500, "INTERNAL_ERROR", response);
        assertFalse(((String) response.get("body")).contains("private"));
    }

    @Test
    void emptyListIsAnArrayAndResponsesAreNotCacheable() throws Exception {
        when(service.list("alice")).thenReturn(List.of());
        var response = call("GET", "/receipts", null);
        assertEquals("[]", response.get("body"));
        assertEquals("application/json", headers(response).get("content-type"));
        assertEquals("no-store", headers(response).get("cache-control"));
        assertEquals(false, response.get("isBase64Encoded"));
    }

    @Test
    void unsupportedRoutesAndMethodsDoNotCallService() {
        assertError(404, "NOT_FOUND", call("GET", "/other", null));
        assertError(404, "NOT_FOUND", call("POST", ITEM_PATH + "/upload-url", CREATE));
        assertError(405, "METHOD_NOT_ALLOWED", call("DELETE", "/receipts", null));
        assertEquals("GET, PUT, DELETE", headers(call("PATCH", ITEM_PATH, METADATA)).get("allow"));
        assertEquals(405, call("POST", "/health", null).get("statusCode"));
        verifyNoInteractions(service);
    }

    @Test
    void malformedGatewayEnvelopesReturn400() {
        assertError(400, "INVALID_REQUEST", handler.handleRequest(null, null));
        assertError(400, "INVALID_REQUEST", handler.handleRequest(Map.of(), null));
        assertError(400, "INVALID_REQUEST", handler.handleRequest(Map.of("version", "2.0"), null));
        var event = event("GET", "/receipts", null);
        event.put("rawPath", 12);
        assertError(400, "INVALID_REQUEST", handler.handleRequest(event, null));
        verifyNoInteractions(service);
    }

    private Map<String, Object> call(String method, String path, String body) {
        return handler.handleRequest(event(method, path, body), null);
    }

    static Map<String, Object> event(String method, String path, String body) {
        var event = new HashMap<String, Object>();
        event.put("version", "2.0");
        event.put("rawPath", path);
        event.put("requestContext", Map.of("http", Map.of("method", method), "requestId", "local-test"));
        if (body != null) {
            event.put("body", body);
        }
        event.put("isBase64Encoded", false);
        return event;
    }

    private static Receipt receipt() {
        Instant now = Instant.parse("2026-09-28T12:00:00Z");
        return new Receipt(ID, "alice", "Store", LocalDate.of(2026, 9, 28),
                new BigDecimal("12345678901234567890.123456789"), "CAD", "Office",
                "alice/image", ReceiptStatus.UPLOADED, now, now);
    }

    private static Map<?, ?> headers(Map<String, Object> response) {
        return (Map<?, ?>) response.get("headers");
    }

    private static JsonNode json(Map<String, Object> response) throws Exception {
        return new ObjectMapper().readTree((String) response.get("body"));
    }

    private static void assertError(int status, String code, Map<String, Object> response) {
        assertEquals(status, response.get("statusCode"), () -> response.toString());
        try {
            assertEquals(code, json(response).path("error").path("code").asText());
        } catch (Exception exception) {
            fail(exception);
        }
    }
}
