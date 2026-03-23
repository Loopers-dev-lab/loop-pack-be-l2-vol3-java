package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Payment save(Payment payment) {
        return paymentJpaRepository.save(payment);
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return paymentJpaRepository.findById(id);
    }

    @Override
    public Optional<Payment> findByOrderId(Long orderId) {
        return paymentJpaRepository.findTopByOrderIdOrderByCreatedAtDesc(orderId);
    }

    @Override
    public Optional<Payment> findByPgTransactionId(String pgTransactionId) {
        return paymentJpaRepository.findByPgTransactionId(pgTransactionId);
    }

    @Override
    public List<Payment> findAllPendingBefore(ZonedDateTime createdAt) {
        return paymentJpaRepository.findAllByStatusAndCreatedAtBefore(PaymentStatus.PENDING, createdAt);
    }
}
