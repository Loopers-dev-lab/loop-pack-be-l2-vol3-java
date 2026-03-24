package com.loopers.domain.payment;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    void save(Payment payment);

    Optional<Payment> findByOrderId(String orderId);

    List<Payment> findPendingPaymentsOlderThan(ZonedDateTime threshold);
}
