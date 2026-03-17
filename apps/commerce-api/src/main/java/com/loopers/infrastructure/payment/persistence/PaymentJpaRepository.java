package com.loopers.infrastructure.payment.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.payment.Payment;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {
}
