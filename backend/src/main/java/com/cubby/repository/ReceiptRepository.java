package com.cubby.repository;

import com.cubby.domain.Receipt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary. userId is the authenticated caller, never request-body identity. */
public interface ReceiptRepository {
    /** Inserts a new receipt; an existing key causes ReceiptWriteConflictException. */
    Receipt save(String userId, Receipt receipt);

    Optional<Receipt> find(String userId, UUID receiptId);

    List<Receipt> listByUser(String userId);

    /**
     * Updates an existing snapshot while preserving identity, createdAt and imageKey.
     * Missing records and immutable-field changes cause a write conflict.
     */
    Receipt update(String userId, Receipt receipt);

    /** Returns false when no receipt existed in this user's partition. */
    boolean delete(String userId, UUID receiptId);
}
