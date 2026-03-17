package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPgOrderCode(String pgOrderCode);
    Optional<Payment> findByPgTransactionId(String pgTransactionId);
    List<Payment> findAllByStatus(PaymentStatus status);
}
