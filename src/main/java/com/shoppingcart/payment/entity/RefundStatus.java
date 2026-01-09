package com.shoppingcart.payment.entity;

/**
 * Refund status lifecycle.
 *
 * Flow: PENDING -> PROCESSING -> COMPLETED
 *       PENDING -> FAILED
 *       PROCESSING -> FAILED
 */
public enum RefundStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
