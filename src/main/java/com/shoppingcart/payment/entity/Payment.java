package com.shoppingcart.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment entity representing a payment transaction.
 *
 * PCI DSS Note: We do NOT store full card numbers (PAN) or CVV.
 * Only gateway tokens, last4, and non-sensitive data are stored.
 */
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payments_order_id", columnList = "orderId"),
    @Index(name = "idx_payments_customer_id", columnList = "customerId"),
    @Index(name = "idx_payments_status", columnList = "status"),
    @Index(name = "idx_payments_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, length = 20)
    private String gateway;

    @Column(length = 255)
    private String gatewayTransactionId;

    @Column(length = 255)
    private String gatewayPaymentIntentId;

    @Column
    private UUID paymentMethodId;

    @Column(length = 4)
    private String cardLast4;

    @Column(length = 20)
    private String cardBrand;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(length = 500)
    private String failureReason;

    @Column(length = 50)
    private String failureCode;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant processedAt;

    @Column
    private Instant completedAt;

    @Column
    private Instant updatedAt;

    @Column(length = 100)
    private String correlationId;

    @Column(length = 50)
    private String idempotencyKey;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) {
            status = PaymentStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
