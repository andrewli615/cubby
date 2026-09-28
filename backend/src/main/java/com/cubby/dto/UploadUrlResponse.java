package com.cubby.dto;

import com.cubby.domain.ReceiptValidation;
import java.net.URI;
import java.time.Instant;

/** Upload authorization value only; constructing it performs no network calls. */
public record UploadUrlResponse(URI uploadUrl, String imageKey, Instant expiresAt) {
    public UploadUrlResponse {
        ReceiptValidation.required(uploadUrl, "uploadUrl");
        if (!"https".equalsIgnoreCase(uploadUrl.getScheme()) || uploadUrl.getHost() == null
                || uploadUrl.getUserInfo() != null || uploadUrl.getFragment() != null) {
            throw new IllegalArgumentException("uploadUrl must be an absolute HTTPS URL without user info or fragment");
        }
        ReceiptValidation.text(imageKey, "imageKey");
        ReceiptValidation.required(expiresAt, "expiresAt");
    }
}
