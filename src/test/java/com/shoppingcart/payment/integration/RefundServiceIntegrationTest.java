package com.shoppingcart.payment.integration;

import com.shoppingcart.payment.entity.Payment;
import com.shoppingcart.payment.entity.PaymentStatus;
import com.shoppingcart.payment.entity.Refund;
import com.shoppingcart.payment.entity.RefundStatus;
import com.shoppingcart.payment.exception.PaymentNotFoundException;
import com.shoppingcart.payment.exception.RefundException;
import com.shoppingcart.payment.repository.PaymentRepository;
import com.shoppingcart.payment.repository.RefundRepository;
import com.shoppingcart.payment.repository.TransactionRepository;
import com.shoppingcart.payment.service.PaymentService;
import com.shoppingcart.payment.service.RefundService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RefundService Integration Tests")
class RefundServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RefundService refundService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private static final String ORDER_ID_PREFIX = "order-refund-it-";
    private static final String CUSTOMER_ID = "customer-refund-test";

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        refundRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Nested
    @DisplayName("Full Refund Processing")
    class FullRefundProcessing {

        @Test
        @DisplayName("should process full refund successfully")
        void shouldProcessFullRefundSuccessfully() {
            // Arrange
            String orderId = ORDER_ID_PREFIX + UUID.randomUUID();
            Payment payment = paymentService.processPayment(
                    orderId,
                    CUSTOMER_ID,
                    new BigDecimal("100.00"),
                    "USD",
                    "mock",
                    null,
                    null,
                    null
            );

            // Act
            Refund refund = refundService.processRefund(
                    payment.getId(),
                    new BigDecimal("100.00"),
                    "Customer requested full refund",
                    null,
                    null
            );

            // Assert
            assertThat(refund.getId()).isNotNull();
            assertThat(refund.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);

            // Verify payment status updated
            Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        }

        @Test
        @DisplayName("should persist refund to database")
        void shouldPersistRefundToDatabase() {
            // Arrange
            String orderId = ORDER_ID_PREFIX + UUID.randomUUID();
            Payment payment = paymentService.processPayment(
                    orderId,
                    CUSTOMER_ID,
                    new BigDecimal("75.00"),
                    "USD",
                    "mock",
                    null,
                    null,
                    null
            );

            // Act
            Refund refund = refundService.processRefund(
                    payment.getId(),
                    new BigDecimal("75.00"),
                    "Test refund",
                    null,
                    null
            );

            // Assert
            var persisted = refundRepository.findById(refund.getId());
            assertThat(persisted).isPresent();
            assertThat(persisted.get().getPaymentId()).isEqualTo(payment.getId());
            assertThat(persisted.get().getReason()).isEqualTo("Test refund");
        }
    }

    @Nested
    @DisplayName("Partial Refund Processing")
    class PartialRefundProcessing {

        @Test
        @DisplayName("should process partial refund successfully")
        void shouldProcessPartialRefundSuccessfully() {
            // Arrange
            String orderId = ORDER_ID_PREFIX + UUID.randomUUID();
            Payment payment = paymentService.processPayment(
                    orderId,
                    CUSTOMER_ID,
                    new BigDecimal("200.00"),
                    "USD",
                    "mock",
                    null,
                    null,
                    null
            );

            // Act
            Refund refund = refundService.processRefund(
                    payment.getId(),
                    new BigDecimal("50.00"),
                    "Partial refund for item",
                    null,
                    null
            );

            // Assert
            assertThat(refund.getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
            assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);

            // Payment should remain completed after partial refund
            Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
            assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("should allow multiple partial refunds")
        void shouldAllowMultiplePartialRefunds() {
            // Arrange
            String orderId = ORDER_ID_PREFIX + UUID.randomUUID();
            Payment payment = paymentService.processPayment(
                    orderId,
                    CUSTOMER_ID,
                    new BigDecimal("100.00"),
                    "USD",
                    "mock",
                    null,
                    null,
                    null
            );

            // Act
            Refund refund1 = refundService.processRefund(
                    payment.getId(),
                    new BigDecimal("30.00"),
                    "First partial refund",
                    null,
                    null
            );

            Refund refund2 = refundService.processRefund(
                    payment.getId(),
                    new BigDecimal("20.00"),
                    "Second partial refund",
                    null,
                    null
            );

            // Assert
            List<Refund> refunds = refundRepository.findByPaymentId(payment.getId());
            assertThat(refunds).hasSize(2);

            BigDecimal totalRefunded = refunds.stream()
                    .map(Refund::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(totalRefunded).isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("should reject refund exceeding remaining amount")
        void shouldRejectRefundExceedingRemainingAmount() {
            // Arrange
            String orderId = ORDER_ID_PREFIX + UUID.randomUUID();
            Payment payment = paymentService.processPayment(
                    orderId,
                    CUSTOMER_ID,
                    new BigDecimal("100.00"),
                    "USD",
                    "mock",
                    null,
                    null,
                    null
            );

            // First refund of 80.00
            refundService.processRefund(
                    payment.getId(),
                    new BigDecimal("80.00"),
                    "First refund",
                    null,
                    null
            );

            // Act & Assert - Try to refund 30.00 (only 20.00 remaining)
            assertThatThrownBy(() -> refundService.processRefund(
                    payment.getId(),
                    new BigDecimal("30.00"),
                    "Exceeding refund",
                    null,
                    null
            )).isInstanceOf(RefundException.class)
                    .hasMessageContaining("exceeds");
        }
    }

    @Nested
    @DisplayName("Refund Validation")
    class RefundValidation {

        @Test
        @DisplayName("should reject refund for non-existent payment")
        void shouldRejectRefundForNonExistentPayment() {
            // Act & Assert
            UUID nonExistentId = UUID.randomUUID();
            assertThatThrownBy(() -> refundService.processRefund(
                    nonExistentId,
                    new BigDecimal("50.00"),
                    "Test",
                    null,
                    null
            )).isInstanceOf(PaymentNotFoundException.class);
        }

        @Test
        @DisplayName("should reject refund for already refunded payment")
        void shouldRejectRefundForAlreadyRefundedPayment() {
            // Arrange
            String orderId = ORDER_ID_PREFIX + UUID.randomUUID();
            Payment payment = paymentService.processPayment(
                    orderId,
                    CUSTOMER_ID,
                    new BigDecimal("50.00"),
                    "USD",
                    "mock",
                    null,
                    null,
                    null
            );

            // First refund - full amount
            refundService.processRefund(
                    payment.getId(),
                    new BigDecimal("50.00"),
                    "Full refund",
                    null,
                    null
            );

            // Act & Assert - Try to refund again
            assertThatThrownBy(() -> refundService.processRefund(
                    payment.getId(),
                    new BigDecimal("10.00"),
                    "Another refund",
                    null,
                    null
            )).isInstanceOf(RefundException.class);
        }
    }

    @Nested
    @DisplayName("Refund Retrieval")
    class RefundRetrieval {

        @Test
        @DisplayName("should find refunds by payment ID")
        void shouldFindRefundsByPaymentId() {
            // Arrange
            String orderId = ORDER_ID_PREFIX + UUID.randomUUID();
            Payment payment = paymentService.processPayment(
                    orderId,
                    CUSTOMER_ID,
                    new BigDecimal("100.00"),
                    "USD",
                    "mock",
                    null,
                    null,
                    null
            );

            refundService.processRefund(
                    payment.getId(),
                    new BigDecimal("25.00"),
                    "First refund",
                    null,
                    null
            );

            refundService.processRefund(
                    payment.getId(),
                    new BigDecimal("25.00"),
                    "Second refund",
                    null,
                    null
            );

            // Act
            List<Refund> refunds = refundService.getRefundsByPayment(payment.getId());

            // Assert
            assertThat(refunds).hasSize(2);
            assertThat(refunds).allMatch(r -> r.getPaymentId().equals(payment.getId()));
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("should handle idempotent refund requests")
        void shouldHandleIdempotentRefundRequests() {
            // Arrange
            String orderId = ORDER_ID_PREFIX + UUID.randomUUID();
            Payment payment = paymentService.processPayment(
                    orderId,
                    CUSTOMER_ID,
                    new BigDecimal("100.00"),
                    "USD",
                    "mock",
                    null,
                    null,
                    null
            );
            String idempotencyKey = "refund-idem-" + UUID.randomUUID();

            // Act - First refund request
            Refund refund1 = refundService.processRefund(
                    payment.getId(),
                    new BigDecimal("50.00"),
                    "Idempotent refund",
                    null,
                    idempotencyKey
            );

            // Act - Second request with same idempotency key
            Refund refund2 = refundService.processRefund(
                    payment.getId(),
                    new BigDecimal("50.00"),
                    "Idempotent refund",
                    null,
                    idempotencyKey
            );

            // Assert
            assertThat(refund2.getId()).isEqualTo(refund1.getId());

            // Should only have one refund in database
            List<Refund> refunds = refundRepository.findByPaymentId(payment.getId());
            assertThat(refunds).hasSize(1);
        }
    }
}
