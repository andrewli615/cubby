package com.cubby.service;

import com.cubby.domain.Receipt;
import com.cubby.dto.CreateReceiptRequest;
import com.cubby.dto.UpdateReceiptRequest;
import com.cubby.dto.UploadUrlRequest;
import com.cubby.dto.UploadUrlResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Receipt operations contract. Each userId must come from verified authentication claims.
 * Implementations must scope all operations and image keys to that owner, preserve
 * receipt identity and createdAt on updates, and assign status and timestamps.
 * DefaultReceiptService implements CRUD through a repository; upload signing and authentication wiring remain deferred.
 */
public interface ReceiptService {
    Receipt create(String userId, CreateReceiptRequest request);

    Receipt update(String userId, UUID receiptId, UpdateReceiptRequest request);

    Optional<Receipt> get(String userId, UUID receiptId);

    List<Receipt> list(String userId);

    void delete(String userId, UUID receiptId);

    UploadUrlResponse createUploadUrl(String userId, UploadUrlRequest request);
}
