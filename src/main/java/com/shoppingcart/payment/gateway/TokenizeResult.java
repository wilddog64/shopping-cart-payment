package com.shoppingcart.payment.gateway;

import lombok.Builder;
import lombok.Data;

/**
 * Result object returned after tokenizing a payment method.
 */
@Data
@Builder
public class TokenizeResult {
    private boolean success;
    private String token;
    private String last4;
    private String brand;
    private String expMonth;
    private String expYear;
    private String errorCode;
    private String errorMessage;

    public static TokenizeResult success(String token, String last4, String brand) {
        return TokenizeResult.builder()
                .success(true)
                .token(token)
                .last4(last4)
                .brand(brand)
                .build();
    }

    public static TokenizeResult failure(String errorCode, String errorMessage) {
        return TokenizeResult.builder()
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
