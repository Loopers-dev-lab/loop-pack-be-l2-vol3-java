package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {
    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByMemberIdAndOrderId(String memberId, UUID orderId);

    Optional<Payment> findByPgTransactionKey(String memberId, String pgTransactionKey);

    List<Payment> findAllByOrderId(UUID orderId);

    List<Payment> findAllByStatusIn(List<PaymentStatus> statuses);
}
