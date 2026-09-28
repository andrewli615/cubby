package com.cubby.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Instant;
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
        var request = new UploadUrlRequest("receipt", contentType);
        assertEquals("receipt", request.fileName());
        assertEquals(contentType, request.contentType());
    }

    @Test
    void retainsUploadAuthorizationValues() {
        var response = new UploadUrlResponse(URL, "owner/image", EXPIRES);
        assertEquals(URL, response.uploadUrl());
        assertEquals("owner/image", response.imageKey());
        assertEquals(EXPIRES, response.expiresAt());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsMissingFileNameOrImageKey(String value) {
        assertThrows(IllegalArgumentException.class, () -> new UploadUrlRequest(value, "image/png"));
        assertThrows(IllegalArgumentException.class, () -> new UploadUrlResponse(URL, value, EXPIRES));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "image", "image/", "/png", "image/png\r\nx-header: value", "image/png; charset=utf-8"})
    void rejectsInvalidContentType(String value) {
        assertThrows(IllegalArgumentException.class, () -> new UploadUrlRequest("receipt", value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.test/image", "/image", "https:image",
            "https://user@example.test/image", "https://example.test/image#fragment"})
    void rejectsInvalidUploadUrls(String value) {
        assertThrows(IllegalArgumentException.class,
                () -> new UploadUrlResponse(URI.create(value), "image", EXPIRES));
    }

    @Test
    void rejectsMissingUrlOrExpiry() {
        assertThrows(IllegalArgumentException.class, () -> new UploadUrlResponse(null, "image", EXPIRES));
        assertThrows(IllegalArgumentException.class, () -> new UploadUrlResponse(URL, "image", null));
    }
}
