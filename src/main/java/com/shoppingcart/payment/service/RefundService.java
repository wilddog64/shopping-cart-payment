package com.shoppingcart.payment.service;

import com.shoppingcart.payment.entity.*;
import com.shoppingcart.payment.gateway.*;
import com.shoppingcart.payment.repository.PaymentRepository;
import com.shoppingcart.payment.repository.RefundRepository;
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
 * Refund processing service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentGatewayRouter gatewayRouter;

    /**
     * Process a refund for a payment.
     */
    @Transactional
    public Refund processRefund(UUID paymentId, BigDecimal amount, String reason,
                                 String initiatedBy, String correlationId) {

        log.info("Processing refund for payment={}, amount={}", paymentId, amount);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        // Validate payment status
        if (payment.getStatus() != PaymentStatus.COMPLETED &&
            payment.getStatus() != PaymentStatus.REFUND_PENDING) {
            throw new IllegalStateException("Cannot refund payment with status: " + payment.getStatus());
        }

        // Validate amount
        BigDecimal totalRefunded = getTotalRefunded(paymentId);
        BigDecimal maxRefundable = payment.getAmount().subtract(totalRefunded);
        if (amount.compareTo(maxRefundable) > 0) {
            throw new IllegalArgumentException(
                    "Refund amount " + amount + " exceeds maximum refundable: " + maxRefundable);
        }

        // Create refund record
        Refund refund = Refund.builder()
                .paymentId(paymentId)
                .amount(amount)
                .currency(payment.getCurrency())
                .status(RefundStatus.PENDING)
                .reason(reason)
                .initiatedBy(initiatedBy)
                .correlationId(correlationId)
                .build();

        refund = refundRepository.save(refund);
        log.info("Created refund record: {}", refund.getId());

        // Update payment status
        payment.setStatus(PaymentStatus.REFUND_PENDING);
        paymentRepository.save(payment);

        // Process through gateway
        refund.setStatus(RefundStatus.PROCESSING);
        refund.setProcessedAt(Instant.now());
        refund = refundRepository.save(refund);

        PaymentGateway gateway = gatewayRouter.getGateway(payment.getGateway());

        RefundRequest request = RefundRequest.builder()
                .paymentTransactionId(payment.getGatewayTransactionId())
                .paymentIntentId(payment.getGatewayPaymentIntentId())
                .amount(amount)
                .currency(payment.getCurrency())
                .reason(reason)
                .correlationId(correlationId)
                .build();

        RefundResult result = gateway.processRefund(request);

        // Record transaction
        Transaction transaction = Transaction.builder()
                .paymentId(paymentId)
                .refundId(refund.getId())
                .type(TransactionType.REFUND)
                .amount(amount)
                .currency(payment.getCurrency())
                .success(result.isSuccess())
                .gatewayTransactionId(result.getRefundId())
                .gatewayResponse(result.getRawResponse())
                .gatewayErrorCode(result.getErrorCode())
                .gatewayErrorMessage(result.getErrorMessage())
                .correlationId(correlationId)
                .build();
        transactionRepository.save(transaction);

        // Update refund based on result
        if (result.isSuccess()) {
            refund.setStatus(RefundStatus.COMPLETED);
            refund.setGatewayRefundId(result.getRefundId());
            refund.setCompletedAt(Instant.now());

            // Update payment status
            BigDecimal newTotalRefunded = totalRefunded.add(amount);
            if (newTotalRefunded.compareTo(payment.getAmount()) >= 0) {
                payment.setStatus(PaymentStatus.REFUNDED);
            } else {
                payment.setStatus(PaymentStatus.COMPLETED); // Partial refund, back to completed
            }
            paymentRepository.save(payment);

            log.info("Refund {} completed successfully, refundId={}", refund.getId(), result.getRefundId());
        } else {
            refund.setStatus(RefundStatus.FAILED);
            refund.setFailureCode(result.getErrorCode());
            refund.setFailureReason(result.getErrorMessage());

            payment.setStatus(PaymentStatus.REFUND_FAILED);
            paymentRepository.save(payment);

            log.warn("Refund {} failed: {} - {}", refund.getId(), result.getErrorCode(), result.getErrorMessage());
        }

        return refundRepository.save(refund);
    }

    /**
     * Get refund by ID.
     */
    public Optional<Refund> getRefund(UUID refundId) {
        return refundRepository.findById(refundId);
    }

    /**
     * Get all refunds for a payment.
     */
    public List<Refund> getRefundsByPayment(UUID paymentId) {
        return refundRepository.findByPaymentId(paymentId);
    }

    /**
     * Get total refunded amount for a payment.
     */
    public BigDecimal getTotalRefunded(UUID paymentId) {
        return refundRepository.findByPaymentId(paymentId).stream()
                .filter(r -> r.getStatus() == RefundStatus.COMPLETED)
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
