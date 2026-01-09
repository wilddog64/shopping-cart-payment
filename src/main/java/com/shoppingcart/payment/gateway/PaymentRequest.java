package com.shoppingcart.payment.gateway;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request object for processing a payment through a gateway.
 */
@Data
@Builder
public class PaymentRequest {
    private String orderId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethodToken;
    private String cardNumber;
    private String cardExpMonth;
    private String cardExpYear;
    private String cardCvc;
    private String cardholderName;
    private String billingEmail;
    private String billingAddressLine1;
    private String billingAddressLine2;
    private String billingCity;
    private String billingState;
    private String billingPostalCode;
    private String billingCountry;
    private String description;
    private String idempotencyKey;
    private String correlationId;
}
