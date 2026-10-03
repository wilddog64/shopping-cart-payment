package com.shoppingcart.payment.dto;

import com.shoppingcart.payment.entity.Payment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentResponseTest {

    @Test
    void fromCopiesGatewayTransactionId() {
        Payment payment = Payment.builder().gatewayTransactionId("mock_txn_abc").build();

        assertEquals("mock_txn_abc", PaymentResponse.from(payment).getGatewayTransactionId());
    }
}
