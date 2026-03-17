package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    // Query

    Optional<Payment> findByTransactionKey(String transactionKey);

    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId ORDER BY p.createdAt DESC LIMIT 1")
    Optional<Payment> findLatestByOrderId(@Param("orderId") Long orderId);

    boolean existsByOrderIdAndStatusIn(Long orderId, java.util.Collection<PaymentStatus> statuses);
}
