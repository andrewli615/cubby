package com.cubby.service;

public final class ReceiptConflictException extends RuntimeException {
    public ReceiptConflictException(Throwable cause) {
        super("Receipt write conflict", cause);
    }
}
