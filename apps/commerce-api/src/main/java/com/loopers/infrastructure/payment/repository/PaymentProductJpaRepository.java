package com.loopers.infrastructure.payment.repository;

import com.loopers.infrastructure.payment.entity.PaymentProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentProductJpaRepository extends JpaRepository<PaymentProductEntity, Long> {

    List<PaymentProductEntity> findByPaymentId(Long paymentId);
}
