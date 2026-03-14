package com.shoppingcart.payment.service;

import com.shoppingcart.payment.entity.Payment;
import com.shoppingcart.payment.entity.PaymentStatus;
import com.shoppingcart.payment.gateway.*;
import com.shoppingcart.payment.repository.PaymentRepository;
import com.shoppingcart.payment.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PaymentGatewayRouter gatewayRouter;

    @Mock
    private PaymentGateway mockGateway;

    @InjectMocks
    private PaymentService paymentService;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    private static final String ORDER_ID = "order-123";
    private static final String CUSTOMER_ID = "customer-456";
    private static final BigDecimal AMOUNT = new BigDecimal("99.99");
    private static final String CURRENCY = "USD";
    private static final String GATEWAY_NAME = "mock";

    @Nested
    @DisplayName("processPayment")
    class ProcessPayment {

        @Test
        @DisplayName("should process payment successfully")
        void shouldProcessPaymentSuccessfully() {
            // Arrange
            when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                if (p.getId() == null) {
                    p.setId(UUID.randomUUID());
                }
                return p;
            });
            when(gatewayRouter.getGatewayOrDefault(GATEWAY_NAME)).thenReturn(mockGateway);
            when(mockGateway.processPayment(any(PaymentRequest.class))).thenReturn(
                    PaymentResult.builder()
                            .success(true)
                            .transactionId("txn_123")
                            .paymentIntentId("pi_123")
                            .cardLast4("4242")
                            .cardBrand("visa")
                            .build()
            );

            // Act
            Payment result = paymentService.processPayment(
                    ORDER_ID, CUSTOMER_ID, AMOUNT, CURRENCY,
                    GATEWAY_NAME, null, null, null
            );

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(result.getCustomerId()).isEqualTo(CUSTOMER_ID);
            assertThat(result.getAmount()).isEqualByComparingTo(AMOUNT);
            assertThat(result.getCurrency()).isEqualTo(CURRENCY);
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(result.getGatewayTransactionId()).isEqualTo("txn_123");
            assertThat(result.getCardLast4()).isEqualTo("4242");
            assertThat(result.getCardBrand()).isEqualTo("visa");

            verify(transactionRepository).save(any());
        }

        @Test
        @DisplayName("should return existing payment for idempotent request")
        void shouldReturnExistingPaymentForIdempotentRequest() {
            // Arrange
            String idempotencyKey = "idem-123";
            Payment existingPayment = Payment.builder()
                    .id(UUID.randomUUID())
                    .orderId(ORDER_ID)
                    .customerId(CUSTOMER_ID)
                    .amount(AMOUNT)
                    .currency(CURRENCY)
                    .status(PaymentStatus.COMPLETED)
                    .build();

            when(paymentRepository.findByIdempotencyKey(idempotencyKey))
                    .thenReturn(Optional.of(existingPayment));

            // Act
            Payment result = paymentService.processPayment(
                    ORDER_ID, CUSTOMER_ID, AMOUNT, CURRENCY,
                    GATEWAY_NAME, null, idempotencyKey, null
            );

            // Assert
            assertThat(result.getId()).isEqualTo(existingPayment.getId());
            verify(gatewayRouter, never()).getGatewayOrDefault(any());
            verify(mockGateway, never()).processPayment(any());
        }

        @Test
        @DisplayName("should return existing completed payment for same order")
        void shouldReturnExistingCompletedPaymentForSameOrder() {
            // Arrange
            Payment existingPayment = Payment.builder()
                    .id(UUID.randomUUID())
                    .orderId(ORDER_ID)
                    .customerId(CUSTOMER_ID)
                    .amount(AMOUNT)
                    .currency(CURRENCY)
                    .status(PaymentStatus.COMPLETED)
                    .build();

            when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existingPayment));

            // Act
            Payment result = paymentService.processPayment(
                    ORDER_ID, CUSTOMER_ID, AMOUNT, CURRENCY,
                    GATEWAY_NAME, null, null, null
            );

            // Assert
            assertThat(result.getId()).isEqualTo(existingPayment.getId());
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            verify(gatewayRouter, never()).getGatewayOrDefault(any());
        }

        @Test
        @DisplayName("should handle gateway failure")
        void shouldHandleGatewayFailure() {
            // Arrange
            when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                if (p.getId() == null) {
                    p.setId(UUID.randomUUID());
                }
                return p;
            });
            when(gatewayRouter.getGatewayOrDefault(GATEWAY_NAME)).thenReturn(mockGateway);
            when(mockGateway.processPayment(any(PaymentRequest.class))).thenReturn(
                    PaymentResult.failure("card_declined", "Your card was declined")
            );

            // Act
            Payment result = paymentService.processPayment(
                    ORDER_ID, CUSTOMER_ID, AMOUNT, CURRENCY,
                    GATEWAY_NAME, null, null, null
            );

            // Assert
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.getFailureCode()).isEqualTo("card_declined");
            assertThat(result.getFailureReason()).isEqualTo("Your card was declined");
        }

        @Test
        @DisplayName("should use default gateway when not specified")
        void shouldUseDefaultGatewayWhenNotSpecified() {
            // Arrange
            when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                if (p.getId() == null) {
                    p.setId(UUID.randomUUID());
                }
                return p;
            });
            when(gatewayRouter.getDefaultGateway()).thenReturn(mockGateway);
            when(gatewayRouter.getGatewayOrDefault(null)).thenReturn(mockGateway);
            when(mockGateway.processPayment(any(PaymentRequest.class))).thenReturn(
                    PaymentResult.builder().success(true).transactionId("txn_123").build()
            );

            // Act
            paymentService.processPayment(
                    ORDER_ID, CUSTOMER_ID, AMOUNT, CURRENCY,
                    null, null, null, null
            );

            // Assert
            verify(gatewayRouter).getGatewayOrDefault(null);
        }

        @Test
        @DisplayName("should set correlation ID on payment")
        void shouldSetCorrelationIdOnPayment() {
            // Arrange
            String correlationId = "corr-123";
            when(paymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                if (p.getId() == null) {
                    p.setId(UUID.randomUUID());
                }
                return p;
            });
            when(gatewayRouter.getGatewayOrDefault(GATEWAY_NAME)).thenReturn(mockGateway);
            when(mockGateway.processPayment(any(PaymentRequest.class))).thenReturn(
                    PaymentResult.builder().success(true).transactionId("txn_123").build()
            );

            // Act
            Payment result = paymentService.processPayment(
                    ORDER_ID, CUSTOMER_ID, AMOUNT, CURRENCY,
                    GATEWAY_NAME, null, null, correlationId
            );

            // Assert
            assertThat(result.getCorrelationId()).isEqualTo(correlationId);
        }
    }

    @Nested
    @DisplayName("getPayment")
    class GetPayment {

        @Test
        @DisplayName("should return payment by ID")
        void shouldReturnPaymentById() {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            Payment payment = Payment.builder()
                    .id(paymentId)
                    .orderId(ORDER_ID)
                    .customerId(CUSTOMER_ID)
                    .amount(AMOUNT)
                    .status(PaymentStatus.COMPLETED)
                    .build();

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

            // Act
            Optional<Payment> result = paymentService.getPayment(paymentId);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(paymentId);
        }

        @Test
        @DisplayName("should return empty for non-existent payment")
        void shouldReturnEmptyForNonExistentPayment() {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

            // Act
            Optional<Payment> result = paymentService.getPayment(paymentId);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getPaymentByOrderId")
    class GetPaymentByOrderId {

        @Test
        @DisplayName("should return payment by order ID")
        void shouldReturnPaymentByOrderId() {
            // Arrange
            Payment payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .orderId(ORDER_ID)
                    .customerId(CUSTOMER_ID)
                    .amount(AMOUNT)
                    .status(PaymentStatus.COMPLETED)
                    .build();

            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));

            // Act
            Optional<Payment> result = paymentService.getPaymentByOrderId(ORDER_ID);

            // Assert
            assertThat(result).isPresent();
            assertThat(result.get().getOrderId()).isEqualTo(ORDER_ID);
        }
    }

    @Nested
    @DisplayName("getPaymentsByCustomer")
    class GetPaymentsByCustomer {

        @Test
        @DisplayName("should return all payments for customer")
        void shouldReturnAllPaymentsForCustomer() {
            // Arrange
            List<Payment> payments = List.of(
                    Payment.builder().id(UUID.randomUUID()).customerId(CUSTOMER_ID).build(),
                    Payment.builder().id(UUID.randomUUID()).customerId(CUSTOMER_ID).build()
            );

            when(paymentRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(payments);

            // Act
            List<Payment> result = paymentService.getPaymentsByCustomer(CUSTOMER_ID);

            // Assert
            assertThat(result).hasSize(2);
        }
    }

    @Nested
    @DisplayName("updatePaymentStatus")
    class UpdatePaymentStatus {

        @Test
        @DisplayName("should update payment status")
        void shouldUpdatePaymentStatus() {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            Payment payment = Payment.builder()
                    .id(paymentId)
                    .orderId(ORDER_ID)
                    .status(PaymentStatus.PENDING)
                    .build();

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

            // Act
            Payment result = paymentService.updatePaymentStatus(paymentId, PaymentStatus.COMPLETED);

            // Assert
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(result.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw exception for non-existent payment")
        void shouldThrowExceptionForNonExistentPayment() {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> paymentService.updatePaymentStatus(paymentId, PaymentStatus.COMPLETED))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Payment not found");
        }
    }
}
