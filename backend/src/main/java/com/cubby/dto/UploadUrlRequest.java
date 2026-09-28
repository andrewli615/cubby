package com.cubby.dto;

import com.cubby.domain.ReceiptValidation;
import java.util.Set;

/** Validated upload declaration; owner, key and expiry are always server-assigned. */
public record UploadUrlRequest(String fileName, String contentType, Long contentLength) {
    public static final long MAX_CONTENT_LENGTH = 10 * 1024 * 1024;
    private static final Set<String> CONTENT_TYPES = Set.of("image/jpeg", "image/png", "application/pdf");

    public UploadUrlRequest {
        ReceiptValidation.text(fileName, "fileName");
        if (fileName.length() > 255 || !fileName.equals(fileName.strip())
                || fileName.equals(".") || fileName.equals("..")
                || fileName.contains("/") || fileName.contains("\\")
                || fileName.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("fileName must be a plain file name of at most 255 characters");
        }
        if (contentType == null || !CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("contentType must be image/jpeg, image/png or application/pdf");
        }
        if (contentLength == null || contentLength < 1 || contentLength > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("contentLength must be between 1 and 10485760 bytes");
        }
    }
}
