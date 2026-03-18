package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByIdForUpdate(Long id);

    Optional<Payment> findByTransactionKey(String transactionKey);

    List<Payment> findByOrderId(Long orderId);

    List<Payment> findByStatusIn(List<PaymentStatus> statuses);

    List<Payment> findPendingByOrderIdAndUserIdForUpdate(Long orderId, Long userId);
}
