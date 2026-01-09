package com.shoppingcart.payment.repository;

import com.shoppingcart.payment.entity.Transaction;
import com.shoppingcart.payment.entity.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByPaymentId(UUID paymentId);

    List<Transaction> findByPaymentIdOrderByCreatedAtDesc(UUID paymentId);

    List<Transaction> findByRefundId(UUID refundId);

    Page<Transaction> findByType(TransactionType type, Pageable pageable);

    Page<Transaction> findByCreatedAtBetween(Instant start, Instant end, Pageable pageable);

    List<Transaction> findByCorrelationId(String correlationId);

    long countByTypeAndSuccess(TransactionType type, boolean success);
}
