package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentStatusHistory;
import com.loopers.domain.payment.PaymentStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PaymentStatusHistoryRepositoryImpl implements PaymentStatusHistoryRepository {

    private final PaymentStatusHistoryJpaRepository jpaRepository;

    @Override
    public PaymentStatusHistory save(PaymentStatusHistory history) {
        return jpaRepository.save(history);
    }

    @Override
    public List<PaymentStatusHistory> findAllByPaymentId(Long paymentId) {
        return jpaRepository.findAllByPaymentIdOrderByCreatedAtAsc(paymentId);
    }
}
