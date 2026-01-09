package com.shoppingcart.payment.repository;

import com.shoppingcart.payment.entity.Payment;
import com.shoppingcart.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(String orderId);

    List<Payment> findByCustomerId(String customerId);

    Page<Payment> findByCustomerId(String customerId, Pageable pageable);

    List<Payment> findByStatus(PaymentStatus status);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByGatewayTransactionId(String gatewayTransactionId);

    List<Payment> findByCustomerIdAndStatus(String customerId, PaymentStatus status);

    List<Payment> findByCreatedAtBetween(Instant start, Instant end);

    long countByStatus(PaymentStatus status);

    long countByCustomerId(String customerId);
}
