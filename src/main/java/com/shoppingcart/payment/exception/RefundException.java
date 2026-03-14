package com.shoppingcart.payment.exception;

/**
 * Minimal exception stub used by integration tests for refund operations.
 */
public class RefundException extends RuntimeException {
    public RefundException(String message) {
        super(message);
    }

    public RefundException(String message, Throwable cause) {
        super(message, cause);
    }
}
