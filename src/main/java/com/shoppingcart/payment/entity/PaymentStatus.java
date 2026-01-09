package com.shoppingcart.payment.entity;

/**
 * Payment status lifecycle.
 *
 * Flow: PENDING -> PROCESSING -> COMPLETED
 *       PENDING -> FAILED
 *       COMPLETED -> REFUND_PENDING -> REFUNDED
 *       COMPLETED -> REFUND_PENDING -> REFUND_FAILED
 */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REFUND_PENDING,
    REFUNDED,
    REFUND_FAILED
}
