package com.loopers.infrastructure.payment.repository.impl;

import com.loopers.domain.payment.model.PaymentProduct;
import com.loopers.domain.payment.repository.PaymentProductRepository;
import com.loopers.infrastructure.payment.entity.PaymentProductEntity;
import com.loopers.infrastructure.payment.repository.PaymentProductJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class PaymentProductRepositoryImpl implements PaymentProductRepository {

    private final PaymentProductJpaRepository paymentProductJpaRepository;

    @Override
    public List<PaymentProduct> saveAll(List<PaymentProduct> paymentProducts) {
        List<PaymentProductEntity> entities = paymentProducts.stream()
                .map(PaymentProductEntity::toEntity)
                .toList();
        return paymentProductJpaRepository.saveAll(entities).stream()
                .map(PaymentProductEntity::toModel)
                .toList();
    }

    @Override
    public List<PaymentProduct> findByPaymentId(Long paymentId) {
        return paymentProductJpaRepository.findByPaymentId(paymentId).stream()
                .map(PaymentProductEntity::toModel)
                .toList();
    }
}
