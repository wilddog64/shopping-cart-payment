package com.shoppingcart.payment.gateway;

import lombok.Builder;
import lombok.Data;

/**
 * Request object for tokenizing a payment method.
 */
@Data
@Builder
public class TokenizeRequest {
    private String customerId;
    private String cardNumber;
    private String cardExpMonth;
    private String cardExpYear;
    private String cardCvc;
    private String cardholderName;
    private String billingEmail;
    private String billingAddressLine1;
    private String billingCity;
    private String billingState;
    private String billingPostalCode;
    private String billingCountry;
}
