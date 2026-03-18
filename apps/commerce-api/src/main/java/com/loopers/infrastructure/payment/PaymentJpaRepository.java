package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionKeyAndDeletedAtIsNull(String transactionKey);

    List<Payment> findByOrderIdAndDeletedAtIsNull(Long orderId);

    List<Payment> findByStatusInAndDeletedAtIsNull(List<PaymentStatus> statuses);

    Optional<Payment> findByIdAndDeletedAtIsNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.orderId = :orderId AND p.userId = :userId AND p.status = :status AND p.deletedAt IS NULL ORDER BY p.id")
    List<Payment> findByOrderIdAndUserIdAndStatusForUpdate(
        @Param("orderId") Long orderId,
        @Param("userId") Long userId,
        @Param("status") PaymentStatus status
    );
}
