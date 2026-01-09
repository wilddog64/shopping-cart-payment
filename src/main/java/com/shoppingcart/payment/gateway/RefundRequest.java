package com.shoppingcart.payment.gateway;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request object for processing a refund through a gateway.
 */
@Data
@Builder
public class RefundRequest {
    private String paymentTransactionId;
    private String paymentIntentId;
    private BigDecimal amount;
    private String currency;
    private String reason;
    private String idempotencyKey;
    private String correlationId;
}
