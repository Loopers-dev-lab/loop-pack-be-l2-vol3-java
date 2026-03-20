package com.loopers.infrastructure.payment.repository.impl;

import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.payment.repository.PaymentRepository;
import com.loopers.infrastructure.payment.entity.PaymentEntity;
import com.loopers.infrastructure.payment.repository.PaymentJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;
    private final EntityManager entityManager;

    @Override
    public Payment save(Payment payment) {
        PaymentEntity entity = paymentJpaRepository.save(PaymentEntity.toEntity(payment));
        entityManager.flush();
        entityManager.clear();
        return entity.toModel();
    }

    @Override
    public Optional<Payment> findById(Long id) {
        return paymentJpaRepository.findById(id).map(PaymentEntity::toModel);
    }

    @Override
    public Optional<Payment> findByOrderId(String orderId) {
        return paymentJpaRepository.findByOrderId(orderId).map(PaymentEntity::toModel);
    }

    @Override
    public Optional<Payment> findByTransactionKey(String transactionKey) {
        return paymentJpaRepository.findByTransactionKey(transactionKey).map(PaymentEntity::toModel);
    }

    @Override
    public void update(Payment payment) {
        PaymentEntity entity = paymentJpaRepository.findById(payment.getId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));
        entity.updateStatus(payment.getStatus(), payment.getTransactionKey(), payment.getFailReason());
        paymentJpaRepository.flush();
    }
}
