package com.cubby.dto;

import com.cubby.domain.ReceiptValidation;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Bearer upload authorization: clients must send the returned signed headers unchanged. */
public record UploadUrlResponse(URI uploadUrl, String imageKey, Instant expiresAt,
                                String method, Map<String, List<String>> headers) {
    public UploadUrlResponse {
        ReceiptValidation.required(uploadUrl, "uploadUrl");
        if (!"https".equalsIgnoreCase(uploadUrl.getScheme()) || uploadUrl.getHost() == null
                || uploadUrl.getUserInfo() != null || uploadUrl.getFragment() != null) {
            throw new IllegalArgumentException("uploadUrl must be an absolute HTTPS URL without user info or fragment");
        }
        ReceiptValidation.text(imageKey, "imageKey");
        ReceiptValidation.required(expiresAt, "expiresAt");
        if (!"PUT".equals(method)) {
            throw new IllegalArgumentException("method must be PUT");
        }
        ReceiptValidation.required(headers, "headers");
        headers = headers.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    @Override
    public String toString() {
        return "UploadUrlResponse[authorization redacted]";
    }
}
