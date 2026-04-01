package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentStatusHistoryJpaRepository extends JpaRepository<PaymentStatusHistory, Long> {
    List<PaymentStatusHistory> findAllByPaymentIdOrderByCreatedAtAsc(Long paymentId);
}
