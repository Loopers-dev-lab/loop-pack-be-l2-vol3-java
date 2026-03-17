package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(Long id);
    Optional<Payment> findByPgOrderCode(String pgOrderCode);
    Optional<Payment> findByPgTransactionId(String pgTransactionId);
    List<Payment> findAllByStatus(PaymentStatus status);
}
