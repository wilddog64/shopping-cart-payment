package com.shoppingcart.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppingcart.payment.dto.ProcessPaymentRequest;
import com.shoppingcart.payment.dto.RefundRequest;
import com.shoppingcart.payment.entity.Payment;
import com.shoppingcart.payment.entity.PaymentStatus;
import com.shoppingcart.payment.entity.Refund;
import com.shoppingcart.payment.entity.RefundStatus;
import com.shoppingcart.payment.service.PaymentService;
import com.shoppingcart.payment.service.RefundService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@DisplayName("PaymentController Tests")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private RefundService refundService;

    private static final String API_BASE = "/api/v1/payments";

    @Nested
    @DisplayName("POST /api/payments")
    class ProcessPayment {

        @Test
        @WithMockUser(roles = "PAYMENT_USER")
        @DisplayName("should process payment successfully")
        void shouldProcessPaymentSuccessfully() throws Exception {
            // Arrange
            ProcessPaymentRequest request = new ProcessPaymentRequest();
            request.setOrderId("order-123");
            request.setCustomerId("customer-456");
            request.setAmount(new BigDecimal("99.99"));
            request.setCurrency("USD");
            request.setGateway("mock");

            Payment payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .orderId("order-123")
                    .customerId("customer-456")
                    .amount(new BigDecimal("99.99"))
                    .currency("USD")
                    .status(PaymentStatus.COMPLETED)
                    .gateway("mock")
                    .gatewayTransactionId("txn_123")
                    .createdAt(Instant.now())
                    .completedAt(Instant.now())
                    .build();

            when(paymentService.processPayment(
                    eq("order-123"), eq("customer-456"),
                    any(BigDecimal.class), eq("USD"),
                    eq("mock"), any(), any(), any()
            )).thenReturn(payment);

            // Act & Assert
            mockMvc.perform(post(API_BASE)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.orderId").value("order-123"))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.amount").value(99.99));
        }

        @Test
        @WithMockUser(roles = "PAYMENT_USER")
        @DisplayName("should return 202 for processing payment")
        void shouldReturn202ForProcessingPayment() throws Exception {
            // Arrange
            ProcessPaymentRequest request = new ProcessPaymentRequest();
            request.setOrderId("order-123");
            request.setCustomerId("customer-456");
            request.setAmount(new BigDecimal("99.99"));
            request.setCurrency("USD");

            Payment payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .orderId("order-123")
                    .status(PaymentStatus.PROCESSING)
                    .build();

            when(paymentService.processPayment(any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(payment);

            // Act & Assert
            mockMvc.perform(post(API_BASE)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isAccepted());
        }

        @Test
        @WithMockUser(roles = "PAYMENT_USER")
        @DisplayName("should validate required fields")
        void shouldValidateRequiredFields() throws Exception {
            // Arrange
            ProcessPaymentRequest request = new ProcessPaymentRequest();
            // Missing required fields

            // Act & Assert
            mockMvc.perform(post(API_BASE)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(roles = "PAYMENT_USER")
        @DisplayName("should validate amount is positive")
        void shouldValidateAmountIsPositive() throws Exception {
            // Arrange
            ProcessPaymentRequest request = new ProcessPaymentRequest();
            request.setOrderId("order-123");
            request.setCustomerId("customer-456");
            request.setAmount(new BigDecimal("-10.00"));
            request.setCurrency("USD");

            // Act & Assert
            mockMvc.perform(post(API_BASE)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should require authentication")
        void shouldRequireAuthentication() throws Exception {
            // Arrange
            ProcessPaymentRequest request = new ProcessPaymentRequest();
            request.setOrderId("order-123");
            request.setCustomerId("customer-456");
            request.setAmount(new BigDecimal("99.99"));
            request.setCurrency("USD");

            // Act & Assert
            mockMvc.perform(post(API_BASE)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "GUEST")
        @DisplayName("should reject unauthorized role")
        void shouldRejectUnauthorizedRole() throws Exception {
            // Arrange
            ProcessPaymentRequest request = new ProcessPaymentRequest();
            request.setOrderId("order-123");
            request.setCustomerId("customer-456");
            request.setAmount(new BigDecimal("99.99"));
            request.setCurrency("USD");

            // Act & Assert
            mockMvc.perform(post(API_BASE)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "PAYMENT_USER")
        @DisplayName("should accept idempotency key header")
        void shouldAcceptIdempotencyKeyHeader() throws Exception {
            // Arrange
            ProcessPaymentRequest request = new ProcessPaymentRequest();
            request.setOrderId("order-123");
            request.setCustomerId("customer-456");
            request.setAmount(new BigDecimal("99.99"));
            request.setCurrency("USD");

            Payment payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .status(PaymentStatus.COMPLETED)
                    .build();

            when(paymentService.processPayment(any(), any(), any(), any(), any(), any(), eq("idem-123"), any()))
                    .thenReturn(payment);

            // Act & Assert
            mockMvc.perform(post(API_BASE)
                            .with(csrf())
                            .header("X-Idempotency-Key", "idem-123")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            verify(paymentService).processPayment(any(), any(), any(), any(), any(), any(), eq("idem-123"), any());
        }
    }

    @Nested
    @DisplayName("GET /api/payments/{paymentId}")
    class GetPayment {

        @Test
        @WithMockUser(roles = "PAYMENT_USER")
        @DisplayName("should return payment by ID")
        void shouldReturnPaymentById() throws Exception {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            Payment payment = Payment.builder()
                    .id(paymentId)
                    .orderId("order-123")
                    .customerId("customer-456")
                    .amount(new BigDecimal("99.99"))
                    .currency("USD")
                    .status(PaymentStatus.COMPLETED)
                    .createdAt(Instant.now())
                    .build();

            when(paymentService.getPayment(paymentId)).thenReturn(Optional.of(payment));

            // Act & Assert
            mockMvc.perform(get(API_BASE + "/{paymentId}", paymentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(paymentId.toString()))
                    .andExpect(jsonPath("$.orderId").value("order-123"));
        }

        @Test
        @WithMockUser(roles = "PAYMENT_USER")
        @DisplayName("should return 404 for non-existent payment")
        void shouldReturn404ForNonExistentPayment() throws Exception {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            when(paymentService.getPayment(paymentId)).thenReturn(Optional.empty());

            // Act & Assert
            mockMvc.perform(get(API_BASE + "/{paymentId}", paymentId))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/payments")
    @WithMockUser(roles = "PAYMENT_USER")
    class GetPayments {

        @Test
        @WithMockUser(roles = "PAYMENT_USER")
        @DisplayName("should return payment by order ID")
        void shouldReturnPaymentByOrderId() throws Exception {
            // Arrange
            Payment payment = Payment.builder()
                    .id(UUID.randomUUID())
                    .orderId("order-123")
                    .status(PaymentStatus.COMPLETED)
                    .createdAt(Instant.now())
                    .build();

            when(paymentService.getPaymentByOrderId("order-123")).thenReturn(Optional.of(payment));

            // Act & Assert
            mockMvc.perform(get(API_BASE).param("orderId", "order-123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].orderId").value("order-123"));
        }

        @Test
        @WithMockUser(roles = "PAYMENT_USER")
        @DisplayName("should return payments by customer ID")
        void shouldReturnPaymentsByCustomerId() throws Exception {
            // Arrange
            List<Payment> payments = List.of(
                    Payment.builder()
                            .id(UUID.randomUUID())
                            .customerId("customer-456")
                            .status(PaymentStatus.COMPLETED)
                            .createdAt(Instant.now())
                            .build(),
                    Payment.builder()
                            .id(UUID.randomUUID())
                            .customerId("customer-456")
                            .status(PaymentStatus.COMPLETED)
                            .createdAt(Instant.now())
                            .build()
            );

            when(paymentService.getPaymentsByCustomer("customer-456")).thenReturn(payments);

            // Act & Assert
            mockMvc.perform(get(API_BASE).param("customerId", "customer-456"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @WithMockUser(roles = "PAYMENT_USER")
        @DisplayName("should return 400 without query params")
        void shouldReturn400WithoutQueryParams() throws Exception {
            // Act & Assert
            mockMvc.perform(get(API_BASE))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/payments/{paymentId}/refund")
    class RefundPayment {

        @Test
        @WithMockUser(roles = "PAYMENT_ADMIN")
        @DisplayName("should process refund successfully")
        void shouldProcessRefundSuccessfully() throws Exception {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            RefundRequest request = new RefundRequest();
            request.setAmount(new BigDecimal("50.00"));
            request.setReason("Customer request");

            Refund refund = Refund.builder()
                    .id(UUID.randomUUID())
                    .paymentId(paymentId)
                    .amount(new BigDecimal("50.00"))
                    .currency("USD")
                    .status(RefundStatus.COMPLETED)
                    .reason("Customer request")
                    .createdAt(Instant.now())
                    .completedAt(Instant.now())
                    .build();

            when(refundService.processRefund(eq(paymentId), any(BigDecimal.class), any(), any(), any()))
                    .thenReturn(refund);

            // Act & Assert
            mockMvc.perform(post(API_BASE + "/{paymentId}/refund", paymentId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").exists())
                    .andExpect(jsonPath("$.paymentId").value(paymentId.toString()))
                    .andExpect(jsonPath("$.amount").value(50.00))
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @WithMockUser(roles = "PAYMENT_USER")
        @DisplayName("should reject refund for non-admin user")
        void shouldRejectRefundForNonAdminUser() throws Exception {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            RefundRequest request = new RefundRequest();
            request.setAmount(new BigDecimal("50.00"));

            // Act & Assert
            mockMvc.perform(post(API_BASE + "/{paymentId}/refund", paymentId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(roles = "PLATFORM_ADMIN")
        @DisplayName("should allow platform admin to refund")
        void shouldAllowPlatformAdminToRefund() throws Exception {
            // Arrange
            UUID paymentId = UUID.randomUUID();
            RefundRequest request = new RefundRequest();
            request.setAmount(new BigDecimal("50.00"));

            Refund refund = Refund.builder()
                    .id(UUID.randomUUID())
                    .paymentId(paymentId)
                    .amount(new BigDecimal("50.00"))
                    .status(RefundStatus.COMPLETED)
                    .createdAt(Instant.now())
                    .build();

            when(refundService.processRefund(any(), any(), any(), any(), any())).thenReturn(refund);

            // Act & Assert
            mockMvc.perform(post(API_BASE + "/{paymentId}/refund", paymentId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());
        }
    }
}
