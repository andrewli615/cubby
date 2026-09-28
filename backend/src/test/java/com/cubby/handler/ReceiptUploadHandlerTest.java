package com.cubby.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cubby.dto.UploadUrlRequest;
import com.cubby.dto.UploadUrlResponse;
import com.cubby.service.ReceiptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReceiptUploadHandlerTest {
    private static final String PATH = "/receipts/upload-url";
    private static final String BODY = "{\"fileName\":\"receipt.png\",\"contentType\":\"image/png\",\"contentLength\":123}";
    private final ReceiptService service = mock(ReceiptService.class);
    private final ReceiptApiHandler handler = new ReceiptApiHandler(service, ignored -> Optional.of("alice"));

    @Test
    void returnsSignedDetailsAndDelegatesOnlyVerifiedOwner() throws Exception {
        var details = new UploadUrlResponse(URI.create("https://uploads.example.test/receipt"),
                "alice/originals/id", Instant.parse("2026-09-28T12:05:00Z"), "PUT",
                Map.of("if-none-match", List.of("*"), "content-type", List.of("image/png")));
        when(service.createUploadUrl(eq("alice"), any())).thenReturn(details);
        var event = ReceiptApiHandlerTest.event("POST", PATH, BODY);
        event.put("headers", Map.of("x-user-id", "bob"));
        event.put("queryStringParameters", Map.of("userId", "bob", "imageKey", "bob/image"));
        var response = handler.handleRequest(event, null);
        assertEquals(200, response.get("statusCode"));
        var json = new ObjectMapper().readTree((String) response.get("body"));
        assertEquals("alice/originals/id", json.get("imageKey").asText());
        assertEquals("PUT", json.get("method").asText());
        assertEquals("*", json.get("headers").get("if-none-match").get(0).asText());
        assertEquals("2026-09-28T12:05:00Z", json.get("expiresAt").asText());
        assertEquals("no-store", ((Map<?, ?>) response.get("headers")).get("cache-control"));
        verify(service).createUploadUrl("alice", new UploadUrlRequest("receipt.png", "image/png", 123L));
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null",
            "{\"fileName\":\"receipt\",\"contentType\":\"image/png\"}",
            "{\"fileName\":\"receipt\",\"contentType\":\"image/png\",\"contentLength\":null}",
            "{\"fileName\":\"receipt\",\"contentType\":\"image/png\",\"contentLength\":0}",
            "{\"fileName\":\"receipt\",\"contentType\":\"image/png\",\"contentLength\":-1}",
            "{\"fileName\":\"receipt\",\"contentType\":\"image/png\",\"contentLength\":10485761}",
            "{\"fileName\":\"receipt\",\"contentType\":\"image/png\",\"contentLength\":1.5}",
            "{\"fileName\":\"receipt\",\"contentType\":\"image/png\",\"contentLength\":\"123\"}",
            "{\"fileName\":\"../receipt\",\"contentType\":\"image/png\",\"contentLength\":123}",
            "{\"fileName\":\"receipt\",\"contentType\":\"image/svg+xml\",\"contentLength\":123}",
            "{\"fileName\":\"receipt\",\"contentType\":\"image/png\",\"contentLength\":123,\"userId\":\"bob\"}",
            "{\"fileName\":\"receipt\",\"contentType\":\"image/png\",\"contentLength\":123,\"imageKey\":\"bob/image\"}",
            "{\"fileName\":\"receipt\",\"contentType\":\"image/png\",\"contentLength\":123,\"expiresIn\":86400}"
    })
    void rejectsInvalidMetadataAndClientAssignedAuthorization(String body) {
        assertEquals(400, handler.handleRequest(ReceiptApiHandlerTest.event("POST", PATH, body), null).get("statusCode"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsAnonymousCallBeforeReadingBody() {
        var anonymous = new ReceiptApiHandler(service, ignored -> Optional.empty());
        assertEquals(401, anonymous.handleRequest(ReceiptApiHandlerTest.event("POST", PATH, "invalid"), null).get("statusCode"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsWrongMethod() {
        assertEquals(405, handler.handleRequest(ReceiptApiHandlerTest.event("GET", PATH, null), null).get("statusCode"));
        verifyNoInteractions(service);
    }

    @Test
    void signingFailureDoesNotLeakAuthorizationOrDiagnostics() {
        when(service.createUploadUrl(anyString(), any())).thenThrow(new IllegalStateException("sensitive signing detail"));
        var response = handler.handleRequest(ReceiptApiHandlerTest.event("POST", PATH, BODY), null);
        assertEquals(500, response.get("statusCode"));
        assertFalse(response.get("body").toString().contains("sensitive"));
    }
}
