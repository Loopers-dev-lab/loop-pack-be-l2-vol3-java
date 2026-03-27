package com.loopers.infrastructure.persistence.jpa.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * PaymentRepository 구현체.
 */
@Repository
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    public PaymentRepositoryImpl(PaymentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Payment save(Payment payment) {
        PaymentJpaEntity entity;
        if (payment.getId() != null) {
            entity = jpaRepository.findById(payment.getId())
                    .orElseThrow(() -> new IllegalStateException("Payment not found: " + payment.getId()));
            entity.setStatus(payment.getStatus());
            entity.setFailReason(payment.getFailReason());
            if (payment.getTransactionId() != null) {
                entity.setTransactionId(payment.getTransactionId());
            }
        } else {
            entity = PaymentMapper.toJpaEntity(payment);
        }
        PaymentJpaEntity saved = jpaRepository.save(entity);
        return PaymentMapper.toDomain(saved);
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return jpaRepository.findById(id)
                .map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByTransactionId(String transactionId) {
        return jpaRepository.findByTransactionId(transactionId)
                .map(PaymentMapper::toDomain);
    }

    @Override
    public Optional<Payment> findByOrderId(Long orderId) {
        return jpaRepository.findByOrderId(orderId)
                .map(PaymentMapper::toDomain);
    }

    @Override
    public List<Payment> findAllPendingPayments() {
        List<PaymentStatus> pendingStatuses = List.of(
                PaymentStatus.PENDING,
                PaymentStatus.PENDING_RETRY
        );
        return jpaRepository.findAllByStatusIn(pendingStatuses).stream()
                .map(PaymentMapper::toDomain)
                .toList();
    }
}
