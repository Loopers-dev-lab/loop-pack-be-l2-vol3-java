package com.loopers.infrastructure.payment.persistence;

import org.springframework.stereotype.Repository;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@link PaymentRepository}의 인프라스트럭처 구현체.
 *
 * <p>{@link PaymentJpaRepository}에 위임하여 결제 영속성을 처리한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public Payment save(Payment payment) {
        return paymentJpaRepository.save(payment);
    }
}
