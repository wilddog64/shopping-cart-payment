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
 * Transaction entity for audit trail of all payment operations.
 *
 * Every payment operation (auth, capture, refund, etc.) creates a transaction record.
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transactions_payment_id", columnList = "paymentId"),
    @Index(name = "idx_transactions_type", columnList = "type"),
    @Index(name = "idx_transactions_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID paymentId;

    @Column
    private UUID refundId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 255)
    private String gatewayTransactionId;

    @Column(columnDefinition = "TEXT")
    private String gatewayResponse;

    @Column(length = 50)
    private String gatewayErrorCode;

    @Column(length = 500)
    private String gatewayErrorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 100)
    private String correlationId;

    @Column(length = 50)
    private String ipAddress;

    @Column(length = 500)
    private String userAgent;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
