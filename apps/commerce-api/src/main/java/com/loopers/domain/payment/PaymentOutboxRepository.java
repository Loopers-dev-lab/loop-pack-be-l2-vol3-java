package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface PaymentOutboxRepository {
    PaymentOutbox save(PaymentOutbox outbox);
    Optional<PaymentOutbox> findById(Long id);
    List<PaymentOutbox> findAllByStatus(PaymentOutboxStatus status);
    Optional<PaymentOutbox> findByPaymentId(Long paymentId);
}
