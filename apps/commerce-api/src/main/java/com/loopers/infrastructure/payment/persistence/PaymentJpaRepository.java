package com.loopers.infrastructure.payment.persistence;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionKey(String transactionKey);

    List<Payment> findByStatusAndUpdatedAtBefore(PaymentStatus status, ZonedDateTime threshold);
}
