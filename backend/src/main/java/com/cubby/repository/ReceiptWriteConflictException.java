package com.cubby.repository;

/** A conditional write failed; no receipt was changed by that operation. */
public final class ReceiptWriteConflictException extends RuntimeException {
    public ReceiptWriteConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
