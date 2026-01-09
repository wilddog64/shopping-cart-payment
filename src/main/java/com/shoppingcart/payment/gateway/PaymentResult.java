package com.shoppingcart.payment.gateway;

import lombok.Builder;
import lombok.Data;

/**
 * Result object returned after processing a payment.
 */
@Data
@Builder
public class PaymentResult {
    private boolean success;
    private String transactionId;
    private String paymentIntentId;
    private String status;
    private String cardLast4;
    private String cardBrand;
    private String errorCode;
    private String errorMessage;
    private String rawResponse;

    public static PaymentResult success(String transactionId, String paymentIntentId) {
        return PaymentResult.builder()
                .success(true)
                .transactionId(transactionId)
                .paymentIntentId(paymentIntentId)
                .status("completed")
                .build();
    }

    public static PaymentResult failure(String errorCode, String errorMessage) {
        return PaymentResult.builder()
                .success(false)
                .status("failed")
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
