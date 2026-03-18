package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Payment save(Payment payment) {
        return paymentJpaRepository.save(payment);
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return paymentJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Optional<Payment> findByIdForUpdate(Long id) {
        return paymentJpaRepository.findByIdForUpdate(id);
    }

    @Override
    public Optional<Payment> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKeyAndDeletedAtIsNull(transactionKey);
    }

    @Override
    public Optional<Payment> findByTransactionKeyForUpdate(String transactionKey) {
        return paymentJpaRepository.findByTransactionKeyForUpdate(transactionKey);
    }

    @Override
    public List<Payment> findByOrderId(Long orderId) {
        return paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(orderId);
    }

    @Override
    public List<Payment> findByStatusIn(List<PaymentStatus> statuses) {
        return paymentJpaRepository.findByStatusInAndDeletedAtIsNull(statuses);
    }

    @Override
    public List<Payment> findPendingByOrderIdAndUserIdForUpdate(Long orderId, Long userId) {
        return paymentJpaRepository.findByOrderIdAndUserIdAndStatusForUpdate(
            orderId, userId, PaymentStatus.PENDING
        );
    }
}
