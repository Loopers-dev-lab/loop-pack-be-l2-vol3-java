package com.loopers.domain.payment;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    // Command

    Payment save(Payment payment);

    // Query

    Optional<Payment> findById(Long id);

    Optional<Payment> findByPaymentKey(String paymentKey);

    Optional<Payment> findLatestByOrderId(Long orderId);

    List<Payment> findByStatusOlderThan(PaymentStatus status, ZonedDateTime threshold);

    boolean existsActiveByOrderId(Long orderId);

    boolean existsSucceededByOrderId(Long orderId);
}
