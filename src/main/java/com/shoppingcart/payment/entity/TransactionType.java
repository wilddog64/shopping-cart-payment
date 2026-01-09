package com.shoppingcart.payment.entity;

/**
 * Types of transactions recorded in the system.
 */
public enum TransactionType {
    AUTHORIZATION,
    CAPTURE,
    CHARGE,
    REFUND,
    VOID,
    CHARGEBACK
}
