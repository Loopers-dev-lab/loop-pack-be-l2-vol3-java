package com.loopers.domain.payment.repository;

import com.loopers.domain.payment.model.Payment;

import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByTransactionKey(String transactionKey);

    void update(Payment payment);
}
