package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 결제 JPA Repository (06 §10.3).
 */
public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    java.util.Optional<PaymentModel> findFirstByOrderIdOrderByCreatedAtDesc(Long orderId);
}
