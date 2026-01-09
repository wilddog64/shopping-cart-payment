package com.shoppingcart.payment.service;

import com.shoppingcart.payment.entity.*;
import com.shoppingcart.payment.gateway.*;
import com.shoppingcart.payment.repository.PaymentRepository;
import com.shoppingcart.payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core payment processing service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentGatewayRouter gatewayRouter;

    /**
     * Process a payment for an order.
     */
    @Transactional
    public Payment processPayment(String orderId, String customerId, BigDecimal amount,
                                   String currency, String gatewayName, String paymentMethodToken,
                                   String idempotencyKey, String correlationId) {

        log.info("Processing payment for order={}, amount={} {}, gateway={}",
                orderId, amount, currency, gatewayName);

        // Check for idempotent request
        if (idempotencyKey != null) {
            Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Returning existing payment for idempotency key: {}", idempotencyKey);
                return existing.get();
            }
        }

        // Check if order already has a payment
        Optional<Payment> existingPayment = paymentRepository.findByOrderId(orderId);
        if (existingPayment.isPresent()) {
            Payment existing = existingPayment.get();
            if (existing.getStatus() == PaymentStatus.COMPLETED) {
                log.warn("Order {} already has completed payment {}", orderId, existing.getId());
                return existing;
            }
        }

        // Create payment record
        Payment payment = Payment.builder()
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .currency(currency.toUpperCase())
                .gateway(gatewayName != null ? gatewayName : gatewayRouter.getDefaultGateway().getName())
                .status(PaymentStatus.PENDING)
                .paymentMethodId(paymentMethodToken != null ? tryParseUUID(paymentMethodToken) : null)
                .idempotencyKey(idempotencyKey)
                .correlationId(correlationId)
                .build();

        payment = paymentRepository.save(payment);
        log.info("Created payment record: {}", payment.getId());

        // Process through gateway
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setProcessedAt(Instant.now());
        payment = paymentRepository.save(payment);

        PaymentGateway gateway = gatewayRouter.getGatewayOrDefault(gatewayName);

        PaymentRequest request = PaymentRequest.builder()
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .currency(currency)
                .paymentMethodToken(paymentMethodToken)
                .idempotencyKey(idempotencyKey)
                .correlationId(correlationId)
                .build();

        PaymentResult result = gateway.processPayment(request);

        // Record transaction
        Transaction transaction = Transaction.builder()
                .paymentId(payment.getId())
                .type(TransactionType.CHARGE)
                .amount(amount)
                .currency(currency.toUpperCase())
                .success(result.isSuccess())
                .gatewayTransactionId(result.getTransactionId())
                .gatewayResponse(result.getRawResponse())
                .gatewayErrorCode(result.getErrorCode())
                .gatewayErrorMessage(result.getErrorMessage())
                .correlationId(correlationId)
                .build();
        transactionRepository.save(transaction);

        // Update payment based on result
        if (result.isSuccess()) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setGatewayTransactionId(result.getTransactionId());
            payment.setGatewayPaymentIntentId(result.getPaymentIntentId());
            payment.setCardLast4(result.getCardLast4());
            payment.setCardBrand(result.getCardBrand());
            payment.setCompletedAt(Instant.now());
            log.info("Payment {} completed successfully, txn={}", payment.getId(), result.getTransactionId());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureCode(result.getErrorCode());
            payment.setFailureReason(result.getErrorMessage());
            log.warn("Payment {} failed: {} - {}", payment.getId(), result.getErrorCode(), result.getErrorMessage());
        }

        return paymentRepository.save(payment);
    }

    /**
     * Get payment by ID.
     */
    public Optional<Payment> getPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId);
    }

    /**
     * Get payment by order ID.
     */
    public Optional<Payment> getPaymentByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    /**
     * Get all payments for a customer.
     */
    public List<Payment> getPaymentsByCustomer(String customerId) {
        return paymentRepository.findByCustomerId(customerId);
    }

    /**
     * Update payment status (for event-driven updates).
     */
    @Transactional
    public Payment updatePaymentStatus(UUID paymentId, PaymentStatus status) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        payment.setStatus(status);
        if (status == PaymentStatus.COMPLETED) {
            payment.setCompletedAt(Instant.now());
        }

        return paymentRepository.save(payment);
    }

    private UUID tryParseUUID(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
