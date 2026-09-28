package com.cubby.dto;

import com.cubby.domain.ReceiptValidation;

/** Describes a file; the service assigns its storage key. */
public record UploadUrlRequest(String fileName, String contentType) {
    public UploadUrlRequest {
        ReceiptValidation.text(fileName, "fileName");
        ReceiptValidation.text(contentType, "contentType");
        if (!contentType.matches("[A-Za-z0-9!#$&^_.+\\-]+/[A-Za-z0-9!#$&^_.+\\-]+")) {
            throw new IllegalArgumentException("contentType must be a media type without parameters");
        }
    }
}
