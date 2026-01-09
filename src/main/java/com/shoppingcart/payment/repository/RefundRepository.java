package com.shoppingcart.payment.repository;

import com.shoppingcart.payment.entity.Refund;
import com.shoppingcart.payment.entity.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {

    List<Refund> findByPaymentId(UUID paymentId);

    List<Refund> findByStatus(RefundStatus status);

    Optional<Refund> findByGatewayRefundId(String gatewayRefundId);

    long countByPaymentId(UUID paymentId);
}
