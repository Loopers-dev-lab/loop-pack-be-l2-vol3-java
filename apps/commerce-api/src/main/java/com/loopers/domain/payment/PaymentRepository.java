package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(Long id);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    Optional<Payment> findByOrderId(Long orderId);
    List<Payment> findAllByStatusAndRequestedBefore(PaymentStatus status, java.time.ZonedDateTime before, int limit);
    List<Payment> findAllByStatusAndFailedBefore(PaymentStatus status, java.time.ZonedDateTime before, int limit);
}
