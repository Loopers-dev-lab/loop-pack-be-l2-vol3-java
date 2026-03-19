package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {
    Optional<PaymentEntity> findByIdempotencyKey(String idempotencyKey);
    Optional<PaymentEntity> findByOrderId(Long orderId);
    List<PaymentEntity> findAllByStatusAndRequestedAtBefore(PaymentStatus status, ZonedDateTime before);
    List<PaymentEntity> findAllByStatusAndFailedAtBefore(PaymentStatus status, ZonedDateTime before);
}
