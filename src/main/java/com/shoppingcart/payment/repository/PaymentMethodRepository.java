package com.shoppingcart.payment.repository;

import com.shoppingcart.payment.entity.PaymentMethod;
import com.shoppingcart.payment.entity.PaymentMethodType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, UUID> {

    List<PaymentMethod> findByCustomerIdAndIsActiveTrue(String customerId);

    Optional<PaymentMethod> findByCustomerIdAndIsDefaultTrue(String customerId);

    Optional<PaymentMethod> findByGatewayToken(String gatewayToken);

    List<PaymentMethod> findByCustomerIdAndType(String customerId, PaymentMethodType type);

    @Modifying
    @Query("UPDATE PaymentMethod pm SET pm.isDefault = false WHERE pm.customerId = :customerId")
    void clearDefaultForCustomer(String customerId);

    long countByCustomerIdAndIsActiveTrue(String customerId);
}
