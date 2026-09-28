package com.cubby.service;

import com.cubby.dto.UploadUrlRequest;
import com.cubby.dto.UploadUrlResponse;

/** Upload boundary; userId must come from the verified identity provider. */
public interface ReceiptUploads {
    UploadUrlResponse createUploadUrl(String userId, UploadUrlRequest request);
}
