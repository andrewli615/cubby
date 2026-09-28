package com.cubby.service;

public final class ReceiptNotFoundException extends RuntimeException {
    public ReceiptNotFoundException() {
        super("Receipt not found");
    }
}
