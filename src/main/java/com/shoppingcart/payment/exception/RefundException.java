package com.shoppingcart.payment.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Minimal exception stub used by integration tests for refund operations.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class RefundException extends RuntimeException {
    public RefundException(String message) {
        super(message);
    }

    public RefundException(String message, Throwable cause) {
        super(message, cause);
    }
}
