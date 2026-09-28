package com.cubby.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UploadUrlTest {
    private static final URI URL = URI.create("https://uploads.example.test/receipt?signature=example");
    private static final Instant EXPIRES = Instant.parse("2026-09-28T12:15:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png", "application/pdf"})
    void acceptsUploadMetadata(String contentType) {
        var request = new UploadUrlRequest("receipt", contentType, 123L);
        assertEquals("receipt", request.fileName());
        assertEquals(contentType, request.contentType());
    }

    @Test
    void retainsUploadAuthorizationValues() {
        var response = new UploadUrlResponse(URL, "owner/image", EXPIRES, "PUT", Map.of());
        assertEquals(URL, response.uploadUrl());
        assertEquals("owner/image", response.imageKey());
        assertEquals(EXPIRES, response.expiresAt());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingFileNameOrImageKey(String value) {
        assertThrows(IllegalArgumentException.class, () -> new UploadUrlRequest(value, "image/png", 123L));
        assertThrows(IllegalArgumentException.class, () -> new UploadUrlResponse(URL, value, EXPIRES, "PUT", Map.of()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "image", "image/", "/png", "image/png\r\nx-header: value", "image/png; charset=utf-8", "text/plain", "image/svg+xml", "IMAGE/PNG"})
    void rejectsInvalidContentType(String value) {
        assertThrows(IllegalArgumentException.class, () -> new UploadUrlRequest("receipt", value, 123L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.test/image", "/image", "https:image",
            "https://user@example.test/image", "https://example.test/image#fragment"})
    void rejectsInvalidUploadUrls(String value) {
        assertThrows(IllegalArgumentException.class,
                () -> new UploadUrlResponse(URI.create(value), "image", EXPIRES, "PUT", Map.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../receipt.png", "a/b.png", "a\\\\b.png", ".", "..", "a\r\nb.png", " receipt.png"})
    void rejectsUnsafeFileNames(String name) {
        assertThrows(IllegalArgumentException.class, () -> new UploadUrlRequest(name, "image/png", 1L));
    }

    @Test
    void rejectsInvalidSizesAndOverlongFileName() {
        for (Long size : new Long[] {null, -1L, 0L, 10485761L}) {
            assertThrows(IllegalArgumentException.class, () -> new UploadUrlRequest("receipt", "image/png", size));
        }
        assertThrows(IllegalArgumentException.class, () -> new UploadUrlRequest("a".repeat(256), "image/png", 1L));
        assertEquals(10485760L, new UploadUrlRequest("receipt", "image/png", 10485760L).contentLength());
    }

    @Test
    void responseHeadersAreDeeplyImmutable() {
        var values = new java.util.ArrayList<>(List.of("*"));
        var headers = new HashMap<String, List<String>>();
        headers.put("if-none-match", values);
        var response = new UploadUrlResponse(URL, "owner/image", EXPIRES, "PUT", headers);
        values.clear();
        headers.clear();
        assertEquals(List.of("*"), response.headers().get("if-none-match"));
        assertThrows(UnsupportedOperationException.class, () -> response.headers().clear());
        assertThrows(UnsupportedOperationException.class, () -> response.headers().get("if-none-match").clear());
        assertThrows(IllegalArgumentException.class, () -> new UploadUrlResponse(URL, "image", EXPIRES, "GET", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new UploadUrlResponse(URL, "image", EXPIRES, "PUT", null));
    }

    @Test
    void rejectsMissingUrlOrExpiry() {
        assertThrows(IllegalArgumentException.class, () -> new UploadUrlResponse(null, "image", EXPIRES, "PUT", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new UploadUrlResponse(URL, "image", null, "PUT", Map.of()));
    }
}
