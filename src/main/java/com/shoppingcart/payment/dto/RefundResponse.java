package com.shoppingcart.payment.dto;

import com.shoppingcart.payment.entity.Refund;
import com.shoppingcart.payment.entity.RefundStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class RefundResponse {
    private UUID id;
    private UUID paymentId;
    private BigDecimal amount;
    private String currency;
    private RefundStatus status;
    private String reason;

    public static RefundResponse from(Refund refund) {
        return RefundResponse.builder()
                .id(refund.getId())
                .paymentId(refund.getPaymentId())
                .amount(refund.getAmount())
                .currency(refund.getCurrency())
                .status(refund.getStatus())
                .reason(refund.getReason())
                .build();
    }
}
