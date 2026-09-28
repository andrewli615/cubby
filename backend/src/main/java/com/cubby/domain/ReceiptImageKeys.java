package com.cubby.domain;

import java.util.UUID;

/** Storage namespaces cannot contain separators, escapes or traversal segments. */
public final class ReceiptImageKeys {
    private ReceiptImageKeys() {}

    public static void requireOwner(String userId) {
        if (userId == null || !userId.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("Invalid image owner");
        }
    }

    public static String original(String userId, UUID uploadId) {
        requireOwner(userId);
        ReceiptValidation.required(uploadId, "uploadId");
        return userId + "/originals/" + uploadId;
    }

    public static void requireOwned(String userId, String imageKey) {
        requireOwner(userId);
        ReceiptValidation.text(imageKey, "imageKey");
        if (!imageKey.startsWith(userId + "/")) {
            throw new IllegalArgumentException("imageKey must belong to the authenticated user");
        }
        for (String segment : imageKey.split("/", -1)) {
            if (!segment.matches("[A-Za-z0-9._-]+") || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid imageKey segment");
            }
        }
    }
}
