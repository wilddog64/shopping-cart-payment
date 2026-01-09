package com.shoppingcart.payment.gateway;

import lombok.Builder;
import lombok.Data;

/**
 * Result object returned after processing a refund.
 */
@Data
@Builder
public class RefundResult {
    private boolean success;
    private String refundId;
    private String status;
    private String errorCode;
    private String errorMessage;
    private String rawResponse;

    public static RefundResult success(String refundId) {
        return RefundResult.builder()
                .success(true)
                .refundId(refundId)
                .status("completed")
                .build();
    }

    public static RefundResult failure(String errorCode, String errorMessage) {
        return RefundResult.builder()
                .success(false)
                .status("failed")
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
