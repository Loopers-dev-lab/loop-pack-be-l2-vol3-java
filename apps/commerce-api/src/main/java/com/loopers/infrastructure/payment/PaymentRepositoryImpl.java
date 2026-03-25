package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
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
        return paymentJpaRepository.findById(id)
                .filter(payment -> payment.getDeletedAt() == null);
    }

    @Override
    public Optional<Payment> findByOrderId(Long orderId) {
        return paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(orderId);
    }

    @Override
    public boolean existsByOrderId(Long orderId) {
        return paymentJpaRepository.findByOrderIdAndDeletedAtIsNull(orderId).isPresent();
    }

    @Override
    public Optional<Payment> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKeyAndDeletedAtIsNull(transactionKey);
    }

    @Override
    public List<Payment> findAllByStatusAndCreatedAtBefore(PaymentStatus status, ZonedDateTime before) {
        return paymentJpaRepository.findAllByStatusAndCreatedAtBeforeAndDeletedAtIsNull(status, before);
    }

    @Override
    public boolean markSuccessIfPending(Long id, String transactionKey) {
        return paymentJpaRepository.markSuccessIfPending(
                id, transactionKey, ZonedDateTime.now(),
                PaymentStatus.SUCCESS, PaymentStatus.PENDING
        ) > 0;
    }

    @Override
    public boolean markFailedIfPending(Long id, String transactionKey, String failureReason) {
        return paymentJpaRepository.markFailedIfPending(
                id, transactionKey, failureReason, ZonedDateTime.now(),
                PaymentStatus.FAILED, PaymentStatus.PENDING
        ) > 0;
    }

    @Override
    public boolean markTimeoutIfPending(Long id) {
        return paymentJpaRepository.markTimeoutIfPending(
                id, "결제 응답 시간 초과",
                PaymentStatus.TIMEOUT, PaymentStatus.PENDING
        ) > 0;
    }
}
