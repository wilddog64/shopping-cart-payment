package com.shoppingcart.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * PaymentMethod entity representing a saved payment method.
 *
 * PCI DSS Note: We store gateway tokens, NOT actual card numbers.
 * Card details (expiry) are encrypted at rest.
 */
@Entity
@Table(name = "payment_methods", indexes = {
    @Index(name = "idx_payment_methods_customer_id", columnList = "customerId"),
    @Index(name = "idx_payment_methods_gateway_token", columnList = "gatewayToken")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethodType type;

    @Column(nullable = false, length = 20)
    private String gateway;

    @Column(nullable = false, length = 255)
    private String gatewayToken;

    @Column(length = 4)
    private String last4;

    @Column(length = 20)
    private String brand;

    @Column(length = 255)
    private String expiryEncrypted;

    @Column(length = 255)
    private String cardholderNameEncrypted;

    @Column(length = 100)
    private String billingEmail;

    @Column(columnDefinition = "TEXT")
    private String billingAddressEncrypted;

    @Column(nullable = false)
    private boolean isDefault;

    @Column(nullable = false)
    private boolean isActive;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    @Column
    private Instant lastUsedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
