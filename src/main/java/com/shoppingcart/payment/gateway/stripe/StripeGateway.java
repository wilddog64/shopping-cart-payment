package com.shoppingcart.payment.gateway.stripe;

import com.shoppingcart.payment.gateway.*;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodCreateParams;
import com.stripe.param.RefundCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Stripe payment gateway implementation.
 *
 * Integrates with Stripe API for payment processing.
 * Credentials should be loaded from Vault in production.
 */
@Component
@Slf4j
public class StripeGateway implements PaymentGateway {

    private static final String NAME = "stripe";

    @Value("${payment.gateway.stripe.enabled:false}")
    private boolean enabled;

    @Value("${payment.gateway.stripe.api-key:}")
    private String apiKey;

    @PostConstruct
    public void init() {
        if (enabled && apiKey != null && !apiKey.isEmpty()) {
            Stripe.apiKey = apiKey;
            log.info("StripeGateway initialized");
        }
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        if (!isEnabled()) {
            return PaymentResult.failure("gateway_disabled", "Stripe gateway is not enabled");
        }

        log.info("StripeGateway: Processing payment for order {} amount {} {}",
                request.getOrderId(), request.getAmount(), request.getCurrency());

        try {
            // Convert amount to cents (Stripe uses smallest currency unit)
            long amountInCents = request.getAmount().multiply(BigDecimal.valueOf(100)).longValue();

            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(request.getCurrency().toLowerCase())
                    .setDescription("Order: " + request.getOrderId())
                    .putMetadata("order_id", request.getOrderId())
                    .putMetadata("customer_id", request.getCustomerId());

            if (request.getPaymentMethodToken() != null) {
                paramsBuilder.setPaymentMethod(request.getPaymentMethodToken());
                paramsBuilder.setConfirm(true);
            }

            if (request.getIdempotencyKey() != null) {
                // Stripe handles idempotency via request options
            }

            PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build());

            log.info("StripeGateway: Payment intent created, id={}, status={}",
                    paymentIntent.getId(), paymentIntent.getStatus());

            if ("succeeded".equals(paymentIntent.getStatus())) {
                return PaymentResult.builder()
                        .success(true)
                        .transactionId(paymentIntent.getLatestCharge())
                        .paymentIntentId(paymentIntent.getId())
                        .status(paymentIntent.getStatus())
                        .build();
            } else if ("requires_action".equals(paymentIntent.getStatus())) {
                return PaymentResult.builder()
                        .success(false)
                        .paymentIntentId(paymentIntent.getId())
                        .status(paymentIntent.getStatus())
                        .errorCode("requires_action")
                        .errorMessage("Additional authentication required")
                        .build();
            } else {
                return PaymentResult.builder()
                        .success(false)
                        .paymentIntentId(paymentIntent.getId())
                        .status(paymentIntent.getStatus())
                        .errorCode("payment_incomplete")
                        .errorMessage("Payment requires confirmation")
                        .build();
            }

        } catch (StripeException e) {
            log.error("StripeGateway: Payment failed", e);
            return PaymentResult.failure(e.getCode(), e.getMessage());
        }
    }

    @Override
    public RefundResult processRefund(RefundRequest request) {
        if (!isEnabled()) {
            return RefundResult.failure("gateway_disabled", "Stripe gateway is not enabled");
        }

        log.info("StripeGateway: Processing refund for payment intent {}",
                request.getPaymentIntentId());

        try {
            long amountInCents = request.getAmount().multiply(BigDecimal.valueOf(100)).longValue();

            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(request.getPaymentIntentId())
                    .setAmount(amountInCents)
                    .setReason(mapRefundReason(request.getReason()))
                    .build();

            Refund refund = Refund.create(params);

            log.info("StripeGateway: Refund created, id={}, status={}",
                    refund.getId(), refund.getStatus());

            if ("succeeded".equals(refund.getStatus())) {
                return RefundResult.success(refund.getId());
            } else {
                return RefundResult.builder()
                        .success(false)
                        .refundId(refund.getId())
                        .status(refund.getStatus())
                        .errorCode("refund_pending")
                        .errorMessage("Refund is pending")
                        .build();
            }

        } catch (StripeException e) {
            log.error("StripeGateway: Refund failed", e);
            return RefundResult.failure(e.getCode(), e.getMessage());
        }
    }

    @Override
    public TokenizeResult tokenize(TokenizeRequest request) {
        if (!isEnabled()) {
            return TokenizeResult.failure("gateway_disabled", "Stripe gateway is not enabled");
        }

        log.info("StripeGateway: Tokenizing card for customer {}", request.getCustomerId());

        try {
            PaymentMethodCreateParams.CardDetails cardDetails = PaymentMethodCreateParams.CardDetails.builder()
                    .setNumber(request.getCardNumber())
                    .setExpMonth(Long.parseLong(request.getCardExpMonth()))
                    .setExpYear(Long.parseLong(request.getCardExpYear()))
                    .setCvc(request.getCardCvc())
                    .build();

            PaymentMethodCreateParams params = PaymentMethodCreateParams.builder()
                    .setType(PaymentMethodCreateParams.Type.CARD)
                    .setCard(cardDetails)
                    .build();

            PaymentMethod paymentMethod = PaymentMethod.create(params);

            String last4 = paymentMethod.getCard() != null ? paymentMethod.getCard().getLast4() : null;
            String brand = paymentMethod.getCard() != null ? paymentMethod.getCard().getBrand() : null;

            log.info("StripeGateway: Payment method created, id={}", paymentMethod.getId());

            return TokenizeResult.builder()
                    .success(true)
                    .token(paymentMethod.getId())
                    .last4(last4)
                    .brand(brand)
                    .expMonth(request.getCardExpMonth())
                    .expYear(request.getCardExpYear())
                    .build();

        } catch (StripeException e) {
            log.error("StripeGateway: Tokenization failed", e);
            return TokenizeResult.failure(e.getCode(), e.getMessage());
        }
    }

    @Override
    public boolean deleteToken(String token) {
        if (!isEnabled()) {
            return false;
        }

        try {
            PaymentMethod paymentMethod = PaymentMethod.retrieve(token);
            paymentMethod.detach();
            return true;
        } catch (StripeException e) {
            log.error("StripeGateway: Failed to delete payment method", e);
            return false;
        }
    }

    @Override
    public boolean supportsRecurring() {
        return true;
    }

    private RefundCreateParams.Reason mapRefundReason(String reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason.toLowerCase()) {
            case "duplicate" -> RefundCreateParams.Reason.DUPLICATE;
            case "fraudulent" -> RefundCreateParams.Reason.FRAUDULENT;
            default -> RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER;
        };
    }
}
