package com.shoppingcart.payment.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shoppingcart.payment.dto.ProcessPaymentRequest;
import com.shoppingcart.payment.dto.RefundRequest;
import com.shoppingcart.payment.entity.Payment;
import com.shoppingcart.payment.entity.PaymentStatus;
import com.shoppingcart.payment.repository.PaymentRepository;
import com.shoppingcart.payment.repository.RefundRepository;
import com.shoppingcart.payment.repository.TransactionRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("PaymentController Integration Tests")
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private static final String BASE_URL = "/api/v1/payments";
    private static final String ORDER_ID_PREFIX = "order-api-it-";
    private static final String CUSTOMER_ID = "customer-api-test";

    @BeforeEach
    void setUp() {
        refundRepository.deleteAll();
        transactionRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Nested
    @DisplayName("POST /api/v1/payments")
    @WithMockUser(roles = "PAYMENT_WRITE")
    class CreatePayment {

        @Test
        @DisplayName("should create payment and return 201")
        void shouldCreatePaymentAndReturn201() throws Exception {
            // Arrange
            ProcessPaymentRequest request = createPaymentRequest();

            // Act & Assert
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderId").value(request.getOrderId()))
                    .andExpect(jsonPath("$.customerId").value(request.getCustomerId()))
                    .andExpect(jsonPath("$.amount").value(99.99))
                    .andExpect(jsonPath("$.currency").value("USD"))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.id").isNotEmpty());
        }

        @Test
        @DisplayName("should persist payment to database")
        void shouldPersistPaymentToDatabase() throws Exception {
            // Arrange
            ProcessPaymentRequest request = createPaymentRequest();

            // Act
            MvcResult result = mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn();

            // Assert
            String responseBody = result.getResponse().getContentAsString();
            Payment response = objectMapper.readValue(responseBody, Payment.class);

            Payment persisted = paymentRepository.findById(response.getId()).orElseThrow();
            assertThat(persisted.getOrderId()).isEqualTo(request.getOrderId());
            assertThat(persisted.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        }

        @Test
        @DisplayName("should return 400 for invalid request")
        void shouldReturn400ForInvalidRequest() throws Exception {
            // Arrange - Missing required fields
            ProcessPaymentRequest request = new ProcessPaymentRequest();

            // Act & Assert
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 400 for negative amount")
        void shouldReturn400ForNegativeAmount() throws Exception {
            // Arrange
            ProcessPaymentRequest request = createPaymentRequest();
            request.setAmount(new BigDecimal("-10.00"));

            // Act & Assert
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should handle idempotency key")
        void shouldHandleIdempotencyKey() throws Exception {
            // Arrange
            ProcessPaymentRequest request = createPaymentRequest();
            String idempotencyKey = "idem-" + UUID.randomUUID();

            // Act - First request
            MvcResult result1 = mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", idempotencyKey)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn();

            // Act - Second request with same key
            MvcResult result2 = mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", idempotencyKey)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andReturn();

            // Assert - Same payment returned
            Payment payment1 = objectMapper.readValue(result1.getResponse().getContentAsString(), Payment.class);
            Payment payment2 = objectMapper.readValue(result2.getResponse().getContentAsString(), Payment.class);
            assertThat(payment2.getId()).isEqualTo(payment1.getId());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/{id}")
    @WithMockUser(roles = "PAYMENT_READ")
    class GetPaymentById {

        @Test
        @DisplayName("should return payment by ID")
        void shouldReturnPaymentById() throws Exception {
            // Arrange
            Payment payment = createAndSavePayment();

            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/{id}", payment.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(payment.getId().toString()))
                    .andExpect(jsonPath("$.orderId").value(payment.getOrderId()))
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("should return 404 for non-existent payment")
        void shouldReturn404ForNonExistentPayment() throws Exception {
            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/{id}", UUID.randomUUID()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/order/{orderId}")
    @WithMockUser(roles = "PAYMENT_READ")
    class GetPaymentByOrderId {

        @Test
        @DisplayName("should return payment by order ID")
        void shouldReturnPaymentByOrderId() throws Exception {
            // Arrange
            Payment payment = createAndSavePayment();

            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/order/{orderId}", payment.getOrderId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(payment.getOrderId()));
        }

        @Test
        @DisplayName("should return 404 for non-existent order")
        void shouldReturn404ForNonExistentOrder() throws Exception {
            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/order/{orderId}", "non-existent-order"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/customer/{customerId}")
    @WithMockUser(roles = "PAYMENT_READ")
    class GetPaymentsByCustomer {

        @Test
        @DisplayName("should return all payments for customer")
        void shouldReturnAllPaymentsForCustomer() throws Exception {
            // Arrange
            String customerId = "customer-" + UUID.randomUUID();
            createAndSavePaymentForCustomer(customerId);
            createAndSavePaymentForCustomer(customerId);
            createAndSavePaymentForCustomer(customerId);

            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/customer/{customerId}", customerId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[*].customerId", everyItem(equalTo(customerId))));
        }

        @Test
        @DisplayName("should return empty list for customer with no payments")
        void shouldReturnEmptyListForCustomerWithNoPayments() throws Exception {
            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/customer/{customerId}", "no-payments-customer"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/payments/{id}/refund")
    @WithMockUser(roles = {"PAYMENT_READ", "PAYMENT_WRITE"})
    class RefundPayment {

        @Test
        @DisplayName("should process full refund")
        void shouldProcessFullRefund() throws Exception {
            // Arrange
            Payment payment = createAndSavePayment();
            RefundRequest refundRequest = new RefundRequest();
            refundRequest.setAmount(payment.getAmount());
            refundRequest.setReason("Customer requested refund");

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{id}/refund", payment.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refundRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amount").value(payment.getAmount().doubleValue()))
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.paymentId").value(payment.getId().toString()));

            // Verify payment status updated
            Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        }

        @Test
        @DisplayName("should process partial refund")
        void shouldProcessPartialRefund() throws Exception {
            // Arrange
            Payment payment = createAndSavePayment();
            RefundRequest refundRequest = new RefundRequest();
            refundRequest.setAmount(new BigDecimal("25.00"));
            refundRequest.setReason("Partial refund");

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{id}/refund", payment.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refundRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.amount").value(25.00));

            // Verify payment status is partially refunded
            Payment updated = paymentRepository.findById(payment.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        }

        @Test
        @DisplayName("should return 404 for non-existent payment")
        void shouldReturn404ForNonExistentPayment() throws Exception {
            // Arrange
            RefundRequest refundRequest = new RefundRequest();
            refundRequest.setAmount(new BigDecimal("10.00"));
            refundRequest.setReason("Test");

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{id}/refund", UUID.randomUUID())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refundRequest)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should return 400 for refund exceeding payment amount")
        void shouldReturn400ForRefundExceedingPaymentAmount() throws Exception {
            // Arrange
            Payment payment = createAndSavePayment();
            RefundRequest refundRequest = new RefundRequest();
            refundRequest.setAmount(payment.getAmount().add(new BigDecimal("100.00")));
            refundRequest.setReason("Too much");

            // Act & Assert
            mockMvc.perform(post(BASE_URL + "/{id}/refund", payment.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refundRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/payments/{id}/refunds")
    @WithMockUser(roles = "PAYMENT_READ")
    class GetRefundsByPayment {

        @Test
        @DisplayName("should return refunds for payment")
        void shouldReturnRefundsForPayment() throws Exception {
            // Arrange
            Payment payment = createAndSavePayment();

            // Create refund via API
            RefundRequest refundRequest = new RefundRequest();
            refundRequest.setAmount(new BigDecimal("25.00"));
            refundRequest.setReason("Test refund");

            mockMvc.perform(post(BASE_URL + "/{id}/refund", payment.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(refundRequest))
                            .with(request -> {
                                request.addUserRole("PAYMENT_WRITE");
                                return request;
                            }))
                    .andExpect(status().isOk());

            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/{id}/refunds", payment.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].amount").value(25.00));
        }
    }

    @Nested
    @DisplayName("Security Tests")
    class SecurityTests {

        @Test
        @DisplayName("should return 401 for unauthenticated request")
        void shouldReturn401ForUnauthenticatedRequest() throws Exception {
            // Act & Assert
            mockMvc.perform(get(BASE_URL + "/{id}", UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "INVALID_ROLE")
        @DisplayName("should return 403 for unauthorized role")
        void shouldReturn403ForUnauthorizedRole() throws Exception {
            // Act & Assert
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createPaymentRequest())))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("Correlation ID")
    @WithMockUser(roles = "PAYMENT_WRITE")
    class CorrelationId {

        @Test
        @DisplayName("should accept and use correlation ID header")
        void shouldAcceptAndUseCorrelationIdHeader() throws Exception {
            // Arrange
            ProcessPaymentRequest request = createPaymentRequest();
            String correlationId = "corr-" + UUID.randomUUID();

            // Act & Assert
            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("X-Correlation-ID", correlationId)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("X-Correlation-ID", correlationId));
        }
    }

    // Helper methods
    private ProcessPaymentRequest createPaymentRequest() {
        ProcessPaymentRequest request = new ProcessPaymentRequest();
        request.setOrderId(ORDER_ID_PREFIX + UUID.randomUUID());
        request.setCustomerId(CUSTOMER_ID);
        request.setAmount(new BigDecimal("99.99"));
        request.setCurrency("USD");
        request.setGateway("mock");
        return request;
    }

    private Payment createAndSavePayment() {
        Payment payment = Payment.builder()
                .orderId(ORDER_ID_PREFIX + UUID.randomUUID())
                .customerId(CUSTOMER_ID)
                .amount(new BigDecimal("99.99"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .gatewayName("mock")
                .gatewayTransactionId("mock-txn-" + UUID.randomUUID())
                .build();
        return paymentRepository.save(payment);
    }

    private Payment createAndSavePaymentForCustomer(String customerId) {
        Payment payment = Payment.builder()
                .orderId(ORDER_ID_PREFIX + UUID.randomUUID())
                .customerId(customerId)
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .status(PaymentStatus.COMPLETED)
                .gatewayName("mock")
                .gatewayTransactionId("mock-txn-" + UUID.randomUUID())
                .build();
        return paymentRepository.save(payment);
    }
}
