package com.shoppingcart.payment.gateway.mock;

import com.shoppingcart.payment.gateway.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock payment gateway for testing and development.
 *
 * Simulates payment processing with configurable delay and failure rate.
 * Use special card numbers for testing:
 * - 4242424242424242: Always succeeds
 * - 4000000000000002: Always declines
 * - 4000000000009995: Insufficient funds
 */
@Component
@Slf4j
public class MockGateway implements PaymentGateway {

    private static final String NAME = "mock";
    private static final Map<String, String> tokens = new ConcurrentHashMap<>();

    @Value("${payment.gateway.mock.enabled:true}")
    private boolean enabled;

    @Value("${payment.gateway.mock.delay-ms:500}")
    private int delayMs;

    @Value("${payment.gateway.mock.failure-rate:0.0}")
    private double failureRate;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public PaymentResult processPayment(PaymentRequest request) {
        log.info("MockGateway: Processing payment for order {} amount {} {}",
                request.getOrderId(), request.getAmount(), request.getCurrency());

        simulateDelay();

        // Check for test card numbers
        if (request.getCardNumber() != null) {
            if (request.getCardNumber().equals("4000000000000002")) {
                return PaymentResult.failure("card_declined", "Your card was declined");
            }
            if (request.getCardNumber().equals("4000000000009995")) {
                return PaymentResult.failure("insufficient_funds", "Insufficient funds");
            }
        }

        // Random failure based on failure rate
        if (shouldFail()) {
            return PaymentResult.failure("processing_error", "Random mock failure for testing");
        }

        String transactionId = "mock_txn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String paymentIntentId = "mock_pi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        String last4 = "4242";
        if (request.getCardNumber() != null && request.getCardNumber().length() >= 4) {
            last4 = request.getCardNumber().substring(request.getCardNumber().length() - 4);
        }

        log.info("MockGateway: Payment successful, txn={}", transactionId);

        return PaymentResult.builder()
                .success(true)
                .transactionId(transactionId)
                .paymentIntentId(paymentIntentId)
                .status("completed")
                .cardLast4(last4)
                .cardBrand("visa")
                .build();
    }

    @Override
    public RefundResult processRefund(RefundRequest request) {
        log.info("MockGateway: Processing refund for txn {} amount {} {}",
                request.getPaymentTransactionId(), request.getAmount(), request.getCurrency());

        simulateDelay();

        if (shouldFail()) {
            return RefundResult.failure("refund_failed", "Random mock failure for testing");
        }

        String refundId = "mock_re_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        log.info("MockGateway: Refund successful, refundId={}", refundId);

        return RefundResult.success(refundId);
    }

    @Override
    public TokenizeResult tokenize(TokenizeRequest request) {
        log.info("MockGateway: Tokenizing card for customer {}", request.getCustomerId());

        simulateDelay();

        String token = "mock_tok_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String last4 = "4242";
        if (request.getCardNumber() != null && request.getCardNumber().length() >= 4) {
            last4 = request.getCardNumber().substring(request.getCardNumber().length() - 4);
        }

        tokens.put(token, request.getCustomerId());

        log.info("MockGateway: Tokenization successful, token={}", token);

        return TokenizeResult.builder()
                .success(true)
                .token(token)
                .last4(last4)
                .brand("visa")
                .expMonth(request.getCardExpMonth())
                .expYear(request.getCardExpYear())
                .build();
    }

    @Override
    public boolean deleteToken(String token) {
        log.info("MockGateway: Deleting token {}", token);
        return tokens.remove(token) != null;
    }

    @Override
    public boolean supportsRecurring() {
        return true;
    }

    private void simulateDelay() {
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean shouldFail() {
        return failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate;
    }
}
