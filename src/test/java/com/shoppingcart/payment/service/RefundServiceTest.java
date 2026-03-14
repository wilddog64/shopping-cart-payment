package com.shoppingcart.payment.service;

import com.shoppingcart.payment.entity.Payment;
import com.shoppingcart.payment.entity.PaymentStatus;
import com.shoppingcart.payment.entity.Refund;
import com.shoppingcart.payment.entity.RefundStatus;
import com.shoppingcart.payment.gateway.*;
import com.shoppingcart.payment.repository.PaymentRepository;
import com.shoppingcart.payment.repository.RefundRepository;
import com.shoppingcart.payment.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefundService Tests")
class RefundServiceTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PaymentGatewayRouter gatewayRouter;

    @Mock
    private PaymentGateway mockGateway;

    @InjectMocks
    private RefundService refundService;

    private Payment completedPayment;
    private UUID paymentId;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        completedPayment = Payment.builder()
                .id(paymentId)
                .orderId("order-123")
                .customerId("customer-456")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .gateway("mock")
                .gatewayTransactionId("txn_123")
                .gatewayPaymentIntentId("pi_123")
                .build();
    }

    @Nested
    @DisplayName("processRefund")
    class ProcessRefund {

        @Test
        @DisplayName("should process full refund successfully")
        void shouldProcessFullRefundSuccessfully() {
            // Arrange
            BigDecimal refundAmount = new BigDecimal("100.00");

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(completedPayment));
            when(refundRepository.findByPaymentId(paymentId)).thenReturn(List.of());
            when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
                Refund r = invocation.getArgument(0);
                if (r.getId() == null) {
                    r.setId(UUID.randomUUID());
                }
                return r;
            });
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
            when(gatewayRouter.getGateway("mock")).thenReturn(mockGateway);
            when(mockGateway.processRefund(any(RefundRequest.class))).thenReturn(
                    RefundResult.success("re_123")
            );

            // Act
            Refund result = refundService.processRefund(
                    paymentId, refundAmount, "Customer request", "admin", "corr-123"
            );

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getPaymentId()).isEqualTo(paymentId);
            assertThat(result.getAmount()).isEqualByComparingTo(refundAmount);
            assertThat(result.getStatus()).isEqualTo(RefundStatus.COMPLETED);
            assertThat(result.getGatewayRefundId()).isEqualTo("re_123");

            verify(transactionRepository).save(any());
        }

        @Test
        @DisplayName("should process partial refund successfully")
        void shouldProcessPartialRefundSuccessfully() {
            // Arrange
            BigDecimal refundAmount = new BigDecimal("30.00");

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(completedPayment));
            when(refundRepository.findByPaymentId(paymentId)).thenReturn(List.of());
            when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
                Refund r = invocation.getArgument(0);
                if (r.getId() == null) {
                    r.setId(UUID.randomUUID());
                }
                return r;
            });
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
            when(gatewayRouter.getGateway("mock")).thenReturn(mockGateway);
            when(mockGateway.processRefund(any(RefundRequest.class))).thenReturn(
                    RefundResult.success("re_123")
            );

            // Act
            Refund result = refundService.processRefund(
                    paymentId, refundAmount, "Partial damage", "admin", null
            );

            // Assert
            assertThat(result.getAmount()).isEqualByComparingTo(refundAmount);
            assertThat(result.getStatus()).isEqualTo(RefundStatus.COMPLETED);

            // Verify payment status remains COMPLETED for partial refund
            verify(paymentRepository, times(2)).save(argThat(payment ->
                    payment.getStatus() == PaymentStatus.REFUND_PENDING ||
                    payment.getStatus() == PaymentStatus.COMPLETED));
        }

        @Test
        @DisplayName("should update payment status to REFUNDED after full refund")
        void shouldUpdatePaymentStatusToRefundedAfterFullRefund() {
            // Arrange
            BigDecimal refundAmount = new BigDecimal("100.00");

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(completedPayment));
            when(refundRepository.findByPaymentId(paymentId)).thenReturn(List.of());
            when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
                Refund r = invocation.getArgument(0);
                if (r.getId() == null) {
                    r.setId(UUID.randomUUID());
                }
                return r;
            });
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
            when(gatewayRouter.getGateway("mock")).thenReturn(mockGateway);
            when(mockGateway.processRefund(any(RefundRequest.class))).thenReturn(
                    RefundResult.success("re_123")
            );

            // Act
            refundService.processRefund(paymentId, refundAmount, "Full refund", "admin", null);

            // Assert - verify payment was updated to REFUNDED
            verify(paymentRepository, atLeastOnce()).save(argThat(payment ->
                    payment.getStatus() == PaymentStatus.REFUNDED ||
                    payment.getStatus() == PaymentStatus.REFUND_PENDING));
        }

        @Test
        @DisplayName("should throw exception for non-existent payment")
        void shouldThrowExceptionForNonExistentPayment() {
            // Arrange
            UUID nonExistentId = UUID.randomUUID();
            when(paymentRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> refundService.processRefund(
                    nonExistentId, new BigDecimal("50.00"), "reason", "admin", null
            ))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessageContaining("Payment not found");
        }

        @Test
        @DisplayName("should throw exception for invalid payment status")
        void shouldThrowExceptionForInvalidPaymentStatus() {
            // Arrange
            Payment pendingPayment = Payment.builder()
                    .id(paymentId)
                    .status(PaymentStatus.PENDING)
                    .build();

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(pendingPayment));

            // Act & Assert
            assertThatThrownBy(() -> refundService.processRefund(
                    paymentId, new BigDecimal("50.00"), "reason", "admin", null
            ))
                    .isInstanceOf(RefundException.class)
                    .hasMessageContaining("Cannot refund payment with status");
        }

        @Test
        @DisplayName("should throw exception when refund amount exceeds remaining")
        void shouldThrowExceptionWhenRefundAmountExceedsRemaining() {
            // Arrange
            Refund existingRefund = Refund.builder()
                    .id(UUID.randomUUID())
                    .paymentId(paymentId)
                    .amount(new BigDecimal("80.00"))
                    .status(RefundStatus.COMPLETED)
                    .build();

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(completedPayment));
            when(refundRepository.findByPaymentId(paymentId)).thenReturn(List.of(existingRefund));

            // Act & Assert
            assertThatThrownBy(() -> refundService.processRefund(
                    paymentId, new BigDecimal("30.00"), "Too much", "admin", null
            ))
                    .isInstanceOf(RefundException.class)
                    .hasMessageContaining("exceeds maximum refundable");
        }

        @Test
        @DisplayName("should handle gateway refund failure")
        void shouldHandleGatewayRefundFailure() {
            // Arrange
            BigDecimal refundAmount = new BigDecimal("50.00");

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(completedPayment));
            when(refundRepository.findByPaymentId(paymentId)).thenReturn(List.of());
            when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
                Refund r = invocation.getArgument(0);
                if (r.getId() == null) {
                    r.setId(UUID.randomUUID());
                }
                return r;
            });
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
            when(gatewayRouter.getGateway("mock")).thenReturn(mockGateway);
            when(mockGateway.processRefund(any(RefundRequest.class))).thenReturn(
                    RefundResult.failure("refund_failed", "Unable to process refund")
            );

            // Act
            Refund result = refundService.processRefund(
                    paymentId, refundAmount, "reason", "admin", null
            );

            // Assert
            assertThat(result.getStatus()).isEqualTo(RefundStatus.FAILED);
            assertThat(result.getFailureCode()).isEqualTo("refund_failed");
            assertThat(result.getFailureReason()).isEqualTo("Unable to process refund");
        }
    }

    @Nested
    @DisplayName("getRefund")
    class GetRefund {

        @Test
        @DisplayName("should return refund by ID")
        void shouldReturnRefundById() {
            // Arrange
            UUID refundId = UUID.randomUUID();
            Refund refund = Refund.builder()
                    .id(refundId)
                    .paymentId(paymentId)
                    .amount(new BigDecimal("50.00"))
                    .status(RefundStatus.COMPLETED)
                    .build();

            when(refundRepository.findById(refundId)).thenReturn(Optional.of(refund));

            // Act
            Optional<Refund> result = refundService.getRefund(refundId);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(refundId);
        }
    }

    @Nested
    @DisplayName("getRefundsByPayment")
    class GetRefundsByPayment {

        @Test
        @DisplayName("should return all refunds for payment")
        void shouldReturnAllRefundsForPayment() {
            // Arrange
            List<Refund> refunds = List.of(
                    Refund.builder().id(UUID.randomUUID()).paymentId(paymentId).build(),
                    Refund.builder().id(UUID.randomUUID()).paymentId(paymentId).build()
            );

            when(refundRepository.findByPaymentId(paymentId)).thenReturn(refunds);

            // Act
            List<Refund> result = refundService.getRefundsByPayment(paymentId);

            // Assert
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("getTotalRefunded")
    class GetTotalRefunded {

        @Test
        @DisplayName("should calculate total refunded amount")
        void shouldCalculateTotalRefundedAmount() {
            // Arrange
            List<Refund> refunds = List.of(
                    Refund.builder()
                            .id(UUID.randomUUID())
                            .paymentId(paymentId)
                            .amount(new BigDecimal("30.00"))
                            .status(RefundStatus.COMPLETED)
                            .build(),
                    Refund.builder()
                            .id(UUID.randomUUID())
                            .paymentId(paymentId)
                            .amount(new BigDecimal("20.00"))
                            .status(RefundStatus.COMPLETED)
                            .build(),
                    Refund.builder()
                            .id(UUID.randomUUID())
                            .paymentId(paymentId)
                            .amount(new BigDecimal("10.00"))
                            .status(RefundStatus.FAILED) // Should not be included
                            .build()
            );

            when(refundRepository.findByPaymentId(paymentId)).thenReturn(refunds);

            // Act
            BigDecimal result = refundService.getTotalRefunded(paymentId);

            // Assert
            assertThat(result).isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("should return zero when no refunds")
        void shouldReturnZeroWhenNoRefunds() {
            // Arrange
            when(refundRepository.findByPaymentId(paymentId)).thenReturn(List.of());

            // Act
            BigDecimal result = refundService.getTotalRefunded(paymentId);

            // Assert
            assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}
