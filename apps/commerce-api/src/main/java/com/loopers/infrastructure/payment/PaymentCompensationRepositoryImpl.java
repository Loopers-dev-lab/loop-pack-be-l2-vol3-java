package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentCompensationModel;
import com.loopers.domain.payment.PaymentCompensationRepository;
import com.loopers.support.enums.CompensationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 도메인 {@link PaymentCompensationRepository} 인터페이스의 인프라스트럭처 구현체.
 */
@Repository
@RequiredArgsConstructor
public class PaymentCompensationRepositoryImpl implements PaymentCompensationRepository {

    private final PaymentCompensationJpaRepository jpaRepository;

    @Override
    public PaymentCompensationModel save(PaymentCompensationModel model) {
        return jpaRepository.save(model);
    }

    @Override
    public List<PaymentCompensationModel> findAllByStatus(CompensationStatus status) {
        return jpaRepository.findAllByStatus(status);
    }

    @Override
    public boolean existsByPaymentId(Long paymentId) {
        return jpaRepository.existsByPaymentId(paymentId);
    }
}
