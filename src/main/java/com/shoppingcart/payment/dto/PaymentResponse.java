package com.shoppingcart.payment.dto;

import com.shoppingcart.payment.entity.Payment;
import com.shoppingcart.payment.entity.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PaymentResponse {
    private UUID id;
    private String orderId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String gateway;
    private String cardLast4;
    private String cardBrand;
    private String failureReason;
    private Instant createdAt;
    private Instant completedAt;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrderId())
                .customerId(payment.getCustomerId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .gateway(payment.getGateway())
                .cardLast4(payment.getCardLast4())
                .cardBrand(payment.getCardBrand())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .completedAt(payment.getCompletedAt())
                .build();
    }
}
