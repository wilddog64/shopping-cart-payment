package com.shoppingcart.payment.gateway;

/**
 * Payment gateway interface for processing payments.
 *
 * Implementations: StripeGateway, PayPalGateway, MockGateway
 */
public interface PaymentGateway {

    /**
     * Get the unique name of this gateway.
     */
    String getName();

    /**
     * Check if this gateway is enabled and configured.
     */
    boolean isEnabled();

    /**
     * Process a payment.
     */
    PaymentResult processPayment(PaymentRequest request);

    /**
     * Process a refund.
     */
    RefundResult processRefund(RefundRequest request);

    /**
     * Tokenize a payment method for future use.
     */
    TokenizeResult tokenize(TokenizeRequest request);

    /**
     * Delete a tokenized payment method.
     */
    boolean deleteToken(String token);

    /**
     * Check if this gateway supports recurring payments.
     */
    default boolean supportsRecurring() {
        return false;
    }

    /**
     * Check if this gateway supports partial refunds.
     */
    default boolean supportsPartialRefund() {
        return true;
    }
}
