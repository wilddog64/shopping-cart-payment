package com.shoppingcart.payment.exception;

/**
 * Minimal exception stub used by integration tests when looking up payments.
 */
public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String message) {
        super(message);
    }

    public PaymentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
