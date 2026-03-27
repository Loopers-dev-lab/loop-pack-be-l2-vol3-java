package com.loopers.infrastructure.persistence.jpa.payment;

import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 결제 JPA Repository.
 */
public interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, Long> {

    Optional<PaymentJpaEntity> findByTransactionId(String transactionId);

    Optional<PaymentJpaEntity> findByOrderId(Long orderId);

    List<PaymentJpaEntity> findAllByStatusIn(List<PaymentStatus> statuses);
}
