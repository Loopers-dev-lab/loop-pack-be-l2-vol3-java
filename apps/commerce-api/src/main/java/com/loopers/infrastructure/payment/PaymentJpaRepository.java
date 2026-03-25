package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {
    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);
    Optional<PaymentEntity> findByOrderId(Long orderId);

    @Query("SELECT p FROM PaymentEntity p WHERE p.status = :status AND p.requestedAt < :before ORDER BY p.requestedAt ASC")
    List<PaymentEntity> findAllByStatusAndRequestedAtBefore(
            @Param("status") PaymentStatus status, @Param("before") ZonedDateTime before, Pageable pageable);

    @Query("SELECT p FROM PaymentEntity p WHERE p.status = :status AND p.failedAt < :before ORDER BY p.failedAt ASC")
    List<PaymentEntity> findAllByStatusAndFailedAtBefore(
            @Param("status") PaymentStatus status, @Param("before") ZonedDateTime before, Pageable pageable);
}
