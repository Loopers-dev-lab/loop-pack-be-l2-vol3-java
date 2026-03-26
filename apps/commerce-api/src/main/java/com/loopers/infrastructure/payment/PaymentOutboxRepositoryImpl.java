package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentOutbox;
import com.loopers.domain.payment.PaymentOutboxRepository;
import com.loopers.domain.payment.PaymentOutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentOutboxRepositoryImpl implements PaymentOutboxRepository {

    private final PaymentOutboxJpaRepository paymentOutboxJpaRepository;

    @Override
    public PaymentOutbox save(PaymentOutbox outbox) {
        return paymentOutboxJpaRepository.save(outbox);
    }

    @Override
    public Optional<PaymentOutbox> findById(Long id) {
        return paymentOutboxJpaRepository.findById(id);
    }

    @Override
    public List<PaymentOutbox> findAllByStatus(PaymentOutboxStatus status) {
        return paymentOutboxJpaRepository.findAllByStatusAndDeletedAtIsNull(status);
    }

    @Override
    public Optional<PaymentOutbox> findByPaymentId(Long paymentId) {
        return paymentOutboxJpaRepository.findByPaymentIdAndDeletedAtIsNull(paymentId);
    }
}
