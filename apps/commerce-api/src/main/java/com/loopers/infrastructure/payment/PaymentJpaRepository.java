package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentStatus;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByRefOrderId(Long refOrderId);

    List<PaymentEntity> findByStatusAndCreatedAtBefore(PaymentStatus status, ZonedDateTime threshold);
}
