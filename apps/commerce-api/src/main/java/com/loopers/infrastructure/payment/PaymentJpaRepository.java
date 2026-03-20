package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    // Query

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    Optional<Payment> findByPaymentKey(String paymentKey);

    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId ORDER BY p.createdAt DESC LIMIT 1")
    Optional<Payment> findLatestByOrderId(@Param("orderId") Long orderId);

    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.createdAt < :threshold")
    List<Payment> findByStatusAndCreatedAtBefore(@Param("status") PaymentStatus status, @Param("threshold") ZonedDateTime threshold);

    boolean existsByOrderIdAndStatusIn(Long orderId, java.util.Collection<PaymentStatus> statuses);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);
}
