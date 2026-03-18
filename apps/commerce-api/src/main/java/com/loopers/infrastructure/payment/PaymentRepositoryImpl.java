package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class PaymentRepositoryImpl implements PaymentRepository {
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
    public Optional<Payment> findByPgOrderCode(String pgOrderCode) {
        return paymentJpaRepository.findByPgOrderCode(pgOrderCode);
    }

    @Override
    public Optional<Payment> findByPgTransactionKey(String pgTransactionId) {
        return paymentJpaRepository.findByPgTransactionKey(pgTransactionId);
    }

    @Override
    public List<Payment> findAllByStatus(PaymentStatus status) {
        return paymentJpaRepository.findAllByStatus(status);
    }
}
