package com.shoppingcart.payment.gateway.stripe;

import com.shoppingcart.payment.gateway.PaymentRequest;
import com.stripe.param.PaymentIntentCreateParams;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class StripeGatewayParamsTest {

    @Test
    void withPaymentMethodTokenSetsAutomaticPaymentMethodsAndConfirms() {
        PaymentRequest request = PaymentRequest.builder()
                .orderId("order-123")
                .customerId("customer-456")
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .paymentMethodToken("pm_card_visa")
                .build();

        PaymentIntentCreateParams params = new StripeGateway()
                .buildPaymentIntentParams(request, 9999)
                .build();

        assertThat(params.getAutomaticPaymentMethods()).isNotNull();
        assertThat(params.getAutomaticPaymentMethods().getEnabled()).isTrue();
        assertThat(params.getAutomaticPaymentMethods().getAllowRedirects())
                .isEqualTo(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER);
        assertThat(params.getConfirm()).isTrue();
        assertThat(params.getPaymentMethod()).isEqualTo("pm_card_visa");
    }

    @Test
    void withoutPaymentMethodTokenLeavesAutomaticPaymentMethodsAndConfirmUnset() {
        PaymentRequest request = PaymentRequest.builder()
                .orderId("order-123")
                .customerId("customer-456")
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .build();

        PaymentIntentCreateParams params = new StripeGateway()
                .buildPaymentIntentParams(request, 9999)
                .build();

        assertThat(params.getAutomaticPaymentMethods()).isNull();
        assertThat(params.getConfirm()).isNull();
    }
}
