package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.support.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 도메인 {@link PaymentRepository} 인터페이스의 인프라스트럭처 구현체.
 */
@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;

    @Override
    public PaymentModel save(PaymentModel payment) {
        return jpaRepository.save(payment);
    }

    @Override
    public Optional<PaymentModel> findById(Long paymentId) {
        return jpaRepository.findById(paymentId);
    }

    @Override
    public Optional<PaymentModel> findByTransactionKey(String transactionKey) {
        return jpaRepository.findByTransactionKey(transactionKey);
    }

    @Override
    public List<PaymentModel> findAllByOrderId(Long orderId) {
        return jpaRepository.findAllByOrderId(orderId);
    }

    @Override
    public boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status) {
        return jpaRepository.existsByOrderIdAndStatus(orderId, status);
    }

    @Override
    public int casUpdateStatus(Long paymentId, PaymentStatus from, PaymentStatus to, String failureReason) {
        return jpaRepository.casUpdateStatus(paymentId, from, to, failureReason);
    }

    @Override
    public List<PaymentModel> findAllRequestedBeforeMinutesAgo(int minutesAgo) {
        return jpaRepository.findAllRequestedBeforeMinutesAgo(minutesAgo);
    }
}
