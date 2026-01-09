package com.shoppingcart.payment.integration;

import com.shoppingcart.payment.entity.Payment;
import com.shoppingcart.payment.entity.PaymentStatus;
import com.shoppingcart.payment.repository.PaymentRepository;
import com.shoppingcart.payment.repository.TransactionRepository;
import com.shoppingcart.payment.service.PaymentService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentService Integration Tests")
class PaymentServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private static final String ORDER_ID_PREFIX = "order-it-";
    private static final String CUSTOMER_ID = "customer-integration-test";

    @BeforeEach
    void setUp() {
        // Clean up test data
        paymentRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    @Nested
    @DisplayName("Payment Processing")
    class PaymentProcessing {

        @Test
        @DisplayName("should persist payment to database")
        void shouldPersistPaymentToDatabase() {
            // Arrange
            String orderId = ORDER_ID_PREFIX + UUID.randomUUID();

            // Act
            Payment payment = paymentService.processPayment(
                    orderId,
                    CUSTOMER_ID,
                    new BigDecimal("99.99"),
                    "USD",
                    "mock",
                    null,
                    null,
                    "corr-123"
            );

            // Assert
            assertThat(payment.getId()).isNotNull();

            Optional<Payment> persisted = paymentRepository.findById(payment.getId());
            assertThat(persisted).isPresent();
            assertThat(persisted.get().getOrderId()).isEqualTo(orderId);
            assertThat(persisted.get().getCustomerId()).isEqualTo(CUSTOMER_ID);
            assertThat(persisted.get().getAmount()).isEqualByComparingTo(new BigDecimal("99.99"));
            assertThat(persisted.get().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("should create transaction record")
        void shouldCreateTransactionRecord() {
            // Arrange
            String orderId = ORDER_ID_PREFIX + UUID.randomUUID();

            // Act
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

            // Assert
            var transactions = transactionRepository.findByPaymentId(payment.getId());
            assertThat(transactions).hasSize(1);
            assertThat(transactions.get(0).isSuccess()).isTrue();
            assertThat(transactions.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
        }

        @Test
        @DisplayName("should handle idempotent requests with database")
        void shouldHandleIdempotentRequestsWithDatabase() {
            // Arrange
            String orderId = ORDER_ID_PREFIX + UUID.randomUUID();
            String idempotencyKey = "idem-" + UUID.randomUUID();

            // Act - First request
            Payment payment1 = paymentService.processPayment(
                    orderId,
                    CUSTOMER_ID,
                    new BigDecimal("100.00"),
                    "USD",
                    "mock",
                    null,
                    idempotencyKey,
                    null
            );

            // Act - Second request with same idempotency key
            Payment payment2 = paymentService.processPayment(
                    orderId + "-different",
                    CUSTOMER_ID,
                    new BigDecimal("100.00"),
                    "USD",
                    "mock",
                    null,
                    idempotencyKey,
                    null
            );

            // Assert
            assertThat(payment2.getId()).isEqualTo(payment1.getId());

            // Should only have one payment in database
            long count = paymentRepository.count();
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("should prevent duplicate payments for same order")
        void shouldPreventDuplicatePaymentsForSameOrder() {
            // Arrange
            String orderId = ORDER_ID_PREFIX + UUID.randomUUID();

            // Act - First payment
            Payment payment1 = paymentService.processPayment(
                    orderId,
                    CUSTOMER_ID,
                    new BigDecimal("75.00"),
                    "USD",
                    "mock",
                    null,
                    null,
                    null
            );

            // Act - Second payment for same order
            Payment payment2 = paymentService.processPayment(
                    orderId,
                    CUSTOMER_ID,
                    new BigDecimal("75.00"),
                    "USD",
                    "mock",
                    null,
                    null,
                    null
            );

            // Assert
            assertThat(payment2.getId()).isEqualTo(payment1.getId());
        }
    }

    @Nested
    @DisplayName("Payment Retrieval")
    class PaymentRetrieval {

        @Test
        @DisplayName("should find payment by order ID")
        void shouldFindPaymentByOrderId() {
            // Arrange
            String orderId = ORDER_ID_PREFIX + UUID.randomUUID();
            paymentService.processPayment(
                    orderId,
                    CUSTOMER_ID,
                    new BigDecimal("50.00"),
                    "USD",
                    "mock",
                    null,
                    null,
                    null
            );

            // Act
            Optional<Payment> found = paymentService.getPaymentByOrderId(orderId);

            // Assert
            assertThat(found).isPresent();
            assertThat(found.get().getOrderId()).isEqualTo(orderId);
        }

        @Test
        @DisplayName("should find all payments by customer")
        void shouldFindAllPaymentsByCustomer() {
            // Arrange
            String customerId = "customer-" + UUID.randomUUID();

            for (int i = 0; i < 3; i++) {
                paymentService.processPayment(
                        ORDER_ID_PREFIX + UUID.randomUUID(),
                        customerId,
                        new BigDecimal("25.00"),
                        "USD",
                        "mock",
                        null,
                        null,
                        null
                );
            }

            // Act
            List<Payment> payments = paymentService.getPaymentsByCustomer(customerId);

            // Assert
            assertThat(payments).hasSize(3);
            payments.forEach(p -> assertThat(p.getCustomerId()).isEqualTo(customerId));
        }
    }

    @Nested
    @DisplayName("Payment Status Updates")
    class PaymentStatusUpdates {

        @Test
        @DisplayName("should update payment status")
        void shouldUpdatePaymentStatus() {
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
            Payment updated = paymentService.updatePaymentStatus(payment.getId(), PaymentStatus.REFUND_PENDING);

            // Assert
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);

            // Verify persisted
            Optional<Payment> persisted = paymentRepository.findById(payment.getId());
            assertThat(persisted.get().getStatus()).isEqualTo(PaymentStatus.REFUND_PENDING);
        }
    }

    @Nested
    @DisplayName("Currency Handling")
    class CurrencyHandling {

        @Test
        @DisplayName("should normalize currency to uppercase")
        void shouldNormalizeCurrencyToUppercase() {
            // Arrange
            String orderId = ORDER_ID_PREFIX + UUID.randomUUID();

            // Act
            Payment payment = paymentService.processPayment(
                    orderId,
                    CUSTOMER_ID,
                    new BigDecimal("50.00"),
                    "usd", // lowercase
                    "mock",
                    null,
                    null,
                    null
            );

            // Assert
            assertThat(payment.getCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("should handle different currencies")
        void shouldHandleDifferentCurrencies() {
            // Arrange & Act
            Payment eurPayment = paymentService.processPayment(
                    ORDER_ID_PREFIX + UUID.randomUUID(),
                    CUSTOMER_ID,
                    new BigDecimal("50.00"),
                    "EUR",
                    "mock",
                    null,
                    null,
                    null
            );

            Payment gbpPayment = paymentService.processPayment(
                    ORDER_ID_PREFIX + UUID.randomUUID(),
                    CUSTOMER_ID,
                    new BigDecimal("50.00"),
                    "GBP",
                    "mock",
                    null,
                    null,
                    null
            );

            // Assert
            assertThat(eurPayment.getCurrency()).isEqualTo("EUR");
            assertThat(gbpPayment.getCurrency()).isEqualTo("GBP");
        }
    }
}
