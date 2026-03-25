package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPgOrderCode(String pgOrderCode);
    Optional<Payment> findByPgTransactionKey(String pgTransactionId);
    List<Payment> findAllByStatus(PaymentStatus status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = 'COMPLETED' WHERE p.id = :id AND p.status = 'PENDING'")
    int completeIfPending(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = 'FAILED', p.failReason = :reason WHERE p.id = :id AND p.status = 'PENDING'")
    int failIfPending(@Param("id") Long id, @Param("reason") String reason);
}
