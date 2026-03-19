package com.loopers.domain.payment;

import java.util.Optional;

public interface PaymentRepository {

    // Command

    Payment save(Payment payment);

    // Query

    Optional<Payment> findById(Long id);

    Optional<Payment> findByPaymentKey(String paymentKey);

    Optional<Payment> findLatestByOrderId(Long orderId);

    boolean existsActiveByOrderId(Long orderId);
}
