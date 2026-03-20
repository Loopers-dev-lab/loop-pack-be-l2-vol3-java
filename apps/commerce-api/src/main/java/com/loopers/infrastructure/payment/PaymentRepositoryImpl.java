package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 결제 Repository 구현체 (06 §10.3).
 */
@Repository
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    public PaymentRepositoryImpl(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public PaymentModel save(PaymentModel payment) {
        return jpaRepository.save(payment);
    }

    @Override
    public Optional<PaymentModel> findById(Long paymentId) {
        return jpaRepository.findById(paymentId);
    }

    @Override
    public boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status) {
        return jpaRepository.existsByOrderIdAndStatus(orderId, status);
    }

    @Override
    public Optional<PaymentModel> findTopByOrderIdOrderByCreatedAtDesc(Long orderId) {
        return jpaRepository.findFirstByOrderIdOrderByCreatedAtDesc(orderId);
    }

    @Override
    public List<PaymentModel> findAllByStatusAndCreatedAtLessThanEqual(PaymentStatus status, ZonedDateTime createdAt) {
        return jpaRepository.findAllByStatusAndCreatedAtLessThanEqual(status, createdAt);
    }
}
