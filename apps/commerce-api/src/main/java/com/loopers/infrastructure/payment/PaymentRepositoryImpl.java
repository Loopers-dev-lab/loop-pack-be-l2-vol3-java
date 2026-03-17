package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private static final List<PaymentStatus> ACTIVE_STATUSES =
            List.of(PaymentStatus.PENDING, PaymentStatus.IN_PROGRESS, PaymentStatus.SUCCEEDED);

    private final PaymentJpaRepository paymentJpaRepository;

    // Command

    @Override
    public Payment save(Payment payment) {
        return paymentJpaRepository.save(payment);
    }

    // Query

    @Override
    public Optional<Payment> findById(Long id) {
        return paymentJpaRepository.findById(id);
    }

    @Override
    public Optional<Payment> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKey(transactionKey);
    }

    @Override
    public Optional<Payment> findLatestByOrderId(Long orderId) {
        return paymentJpaRepository.findLatestByOrderId(orderId);
    }

    @Override
    public boolean existsActiveByOrderId(Long orderId) {
        return paymentJpaRepository.existsByOrderIdAndStatusIn(orderId, ACTIVE_STATUSES);
    }
}
