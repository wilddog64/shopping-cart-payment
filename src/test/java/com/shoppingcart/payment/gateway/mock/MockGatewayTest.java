package com.shoppingcart.payment.gateway.mock;

import com.shoppingcart.payment.gateway.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MockGateway Tests")
class MockGatewayTest {

    private MockGateway mockGateway;

    @BeforeEach
    void setUp() {
        mockGateway = new MockGateway();
        ReflectionTestUtils.setField(mockGateway, "enabled", true);
        ReflectionTestUtils.setField(mockGateway, "delayMs", 0); // No delay for tests
        ReflectionTestUtils.setField(mockGateway, "failureRate", 0.0);
    }

    @Nested
    @DisplayName("Gateway Properties")
    class GatewayProperties {

        @Test
        @DisplayName("should return correct gateway name")
        void shouldReturnCorrectGatewayName() {
            assertThat(mockGateway.getName()).isEqualTo("mock");
        }

        @Test
        @DisplayName("should return enabled status")
        void shouldReturnEnabledStatus() {
            assertThat(mockGateway.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should return disabled when configured")
        void shouldReturnDisabledWhenConfigured() {
            ReflectionTestUtils.setField(mockGateway, "enabled", false);
            assertThat(mockGateway.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("should support recurring payments")
        void shouldSupportRecurringPayments() {
            assertThat(mockGateway.supportsRecurring()).isTrue();
        }

        @Test
        @DisplayName("should support partial refunds")
        void shouldSupportPartialRefunds() {
            assertThat(mockGateway.supportsPartialRefund()).isTrue();
        }
    }

    @Nested
    @DisplayName("processPayment")
    class ProcessPayment {

        @Test
        @DisplayName("should process payment successfully")
        void shouldProcessPaymentSuccessfully() {
            // Arrange
            PaymentRequest request = PaymentRequest.builder()
                    .orderId("order-123")
                    .customerId("customer-456")
                    .amount(new BigDecimal("99.99"))
                    .currency("USD")
                    .build();

            // Act
            PaymentResult result = mockGateway.processPayment(request);

            // Assert
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getTransactionId()).startsWith("mock_txn_");
            assertThat(result.getPaymentIntentId()).startsWith("mock_pi_");
            assertThat(result.getCardLast4()).isEqualTo("4242");
            assertThat(result.getCardBrand()).isEqualTo("visa");
        }

        @Test
        @DisplayName("should extract last4 from card number")
        void shouldExtractLast4FromCardNumber() {
            // Arrange
            PaymentRequest request = PaymentRequest.builder()
                    .orderId("order-123")
                    .customerId("customer-456")
                    .amount(new BigDecimal("99.99"))
                    .currency("USD")
                    .cardNumber("5555555555554444")
                    .build();

            // Act
            PaymentResult result = mockGateway.processPayment(request);

            // Assert
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getCardLast4()).isEqualTo("4444");
        }

        @Test
        @DisplayName("should decline card ending in 0002")
        void shouldDeclineCardEndingIn0002() {
            // Arrange
            PaymentRequest request = PaymentRequest.builder()
                    .orderId("order-123")
                    .customerId("customer-456")
                    .amount(new BigDecimal("99.99"))
                    .currency("USD")
                    .cardNumber("4000000000000002")
                    .build();

            // Act
            PaymentResult result = mockGateway.processPayment(request);

            // Assert
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorCode()).isEqualTo("card_declined");
            assertThat(result.getErrorMessage()).isEqualTo("Your card was declined");
        }

        @Test
        @DisplayName("should return insufficient funds for card ending in 9995")
        void shouldReturnInsufficientFundsForCard9995() {
            // Arrange
            PaymentRequest request = PaymentRequest.builder()
                    .orderId("order-123")
                    .customerId("customer-456")
                    .amount(new BigDecimal("99.99"))
                    .currency("USD")
                    .cardNumber("4000000000009995")
                    .build();

            // Act
            PaymentResult result = mockGateway.processPayment(request);

            // Assert
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorCode()).isEqualTo("insufficient_funds");
            assertThat(result.getErrorMessage()).isEqualTo("Insufficient funds");
        }

        @Test
        @DisplayName("should succeed with test card 4242")
        void shouldSucceedWithTestCard4242() {
            // Arrange
            PaymentRequest request = PaymentRequest.builder()
                    .orderId("order-123")
                    .customerId("customer-456")
                    .amount(new BigDecimal("99.99"))
                    .currency("USD")
                    .cardNumber("4242424242424242")
                    .build();

            // Act
            PaymentResult result = mockGateway.processPayment(request);

            // Assert
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getCardLast4()).isEqualTo("4242");
        }

        @Test
        @DisplayName("should respect failure rate configuration")
        void shouldRespectFailureRateConfiguration() {
            // Arrange
            ReflectionTestUtils.setField(mockGateway, "failureRate", 1.0); // 100% failure

            PaymentRequest request = PaymentRequest.builder()
                    .orderId("order-123")
                    .customerId("customer-456")
                    .amount(new BigDecimal("99.99"))
                    .currency("USD")
                    .build();

            // Act
            PaymentResult result = mockGateway.processPayment(request);

            // Assert
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorCode()).isEqualTo("processing_error");
        }
    }

    @Nested
    @DisplayName("processRefund")
    class ProcessRefund {

        @Test
        @DisplayName("should process refund successfully")
        void shouldProcessRefundSuccessfully() {
            // Arrange
            RefundRequest request = RefundRequest.builder()
                    .paymentTransactionId("txn_123")
                    .paymentIntentId("pi_123")
                    .amount(new BigDecimal("50.00"))
                    .currency("USD")
                    .reason("Customer request")
                    .build();

            // Act
            RefundResult result = mockGateway.processRefund(request);

            // Assert
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getRefundId()).startsWith("mock_re_");
        }

        @Test
        @DisplayName("should fail refund with failure rate configured")
        void shouldFailRefundWithFailureRateConfigured() {
            // Arrange
            ReflectionTestUtils.setField(mockGateway, "failureRate", 1.0);

            RefundRequest request = RefundRequest.builder()
                    .paymentTransactionId("txn_123")
                    .amount(new BigDecimal("50.00"))
                    .currency("USD")
                    .build();

            // Act
            RefundResult result = mockGateway.processRefund(request);

            // Assert
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorCode()).isEqualTo("refund_failed");
        }
    }

    @Nested
    @DisplayName("tokenize")
    class Tokenize {

        @Test
        @DisplayName("should tokenize card successfully")
        void shouldTokenizeCardSuccessfully() {
            // Arrange
            TokenizeRequest request = TokenizeRequest.builder()
                    .customerId("customer-456")
                    .cardNumber("4242424242424242")
                    .cardExpMonth("12")
                    .cardExpYear("2025")
                    .cardCvc("123")
                    .build();

            // Act
            TokenizeResult result = mockGateway.tokenize(request);

            // Assert
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getToken()).startsWith("mock_tok_");
            assertThat(result.getLast4()).isEqualTo("4242");
            assertThat(result.getBrand()).isEqualTo("visa");
            assertThat(result.getExpMonth()).isEqualTo("12");
            assertThat(result.getExpYear()).isEqualTo("2025");
        }

        @Test
        @DisplayName("should extract last4 from different card numbers")
        void shouldExtractLast4FromDifferentCardNumbers() {
            // Arrange
            TokenizeRequest request = TokenizeRequest.builder()
                    .customerId("customer-456")
                    .cardNumber("5555555555554444")
                    .cardExpMonth("12")
                    .cardExpYear("2025")
                    .cardCvc("123")
                    .build();

            // Act
            TokenizeResult result = mockGateway.tokenize(request);

            // Assert
            assertThat(result.getLast4()).isEqualTo("4444");
        }
    }

    @Nested
    @DisplayName("deleteToken")
    class DeleteToken {

        @Test
        @DisplayName("should delete existing token")
        void shouldDeleteExistingToken() {
            // Arrange - First create a token
            TokenizeRequest request = TokenizeRequest.builder()
                    .customerId("customer-456")
                    .cardNumber("4242424242424242")
                    .cardExpMonth("12")
                    .cardExpYear("2025")
                    .build();
            TokenizeResult tokenResult = mockGateway.tokenize(request);

            // Act
            boolean deleted = mockGateway.deleteToken(tokenResult.getToken());

            // Assert
            assertThat(deleted).isTrue();
        }

        @Test
        @DisplayName("should return false for non-existent token")
        void shouldReturnFalseForNonExistentToken() {
            // Act
            boolean deleted = mockGateway.deleteToken("non_existent_token");

            // Assert
            assertThat(deleted).isFalse();
        }
    }
}
