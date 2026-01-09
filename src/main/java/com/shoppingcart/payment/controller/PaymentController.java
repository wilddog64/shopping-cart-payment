package com.shoppingcart.payment.controller;

import com.shoppingcart.payment.dto.PaymentResponse;
import com.shoppingcart.payment.dto.ProcessPaymentRequest;
import com.shoppingcart.payment.entity.Payment;
import com.shoppingcart.payment.service.PaymentService;
import com.shoppingcart.payment.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;
    private final RefundService refundService;

    @PostMapping
    @PreAuthorize("hasAnyRole('PAYMENT_USER', 'PAYMENT_ADMIN', 'PLATFORM_ADMIN')")
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody ProcessPaymentRequest request,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {

        log.info("Processing payment request for order: {}", request.getOrderId());

        String actualIdempotencyKey = idempotencyKey != null ? idempotencyKey : request.getIdempotencyKey();

        Payment payment = paymentService.processPayment(
                request.getOrderId(),
                request.getCustomerId(),
                request.getAmount(),
                request.getCurrency(),
                request.getGateway(),
                request.getPaymentMethodId(),
                actualIdempotencyKey,
                correlationId
        );

        HttpStatus status = payment.getStatus().name().equals("COMPLETED")
                ? HttpStatus.CREATED
                : HttpStatus.ACCEPTED;

        return ResponseEntity.status(status).body(PaymentResponse.from(payment));
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('PAYMENT_USER', 'PAYMENT_ADMIN', 'PLATFORM_ADMIN')")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID paymentId) {
        return paymentService.getPayment(paymentId)
                .map(payment -> ResponseEntity.ok(PaymentResponse.from(payment)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PAYMENT_USER', 'PAYMENT_ADMIN', 'PLATFORM_ADMIN')")
    public ResponseEntity<List<PaymentResponse>> getPayments(
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String customerId) {

        if (orderId != null) {
            return paymentService.getPaymentByOrderId(orderId)
                    .map(payment -> ResponseEntity.ok(List.of(PaymentResponse.from(payment))))
                    .orElse(ResponseEntity.ok(List.of()));
        }

        if (customerId != null) {
            List<PaymentResponse> payments = paymentService.getPaymentsByCustomer(customerId)
                    .stream()
                    .map(PaymentResponse::from)
                    .toList();
            return ResponseEntity.ok(payments);
        }

        return ResponseEntity.badRequest().build();
    }

    @PostMapping("/{paymentId}/refund")
    @PreAuthorize("hasAnyRole('PAYMENT_ADMIN', 'PLATFORM_ADMIN')")
    public ResponseEntity<?> refundPayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody com.shoppingcart.payment.dto.RefundRequest request,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestHeader(value = "X-User-ID", required = false) String userId) {

        log.info("Processing refund request for payment: {}", paymentId);

        var refund = refundService.processRefund(
                paymentId,
                request.getAmount(),
                request.getReason(),
                userId,
                correlationId
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(refund);
    }
}
