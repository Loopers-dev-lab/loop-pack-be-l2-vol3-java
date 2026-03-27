package com.loopers.domain.payment;

import java.util.List;
import java.util.Optional;

/**
 * 결제 Repository 인터페이스.
 * 순수 Java 인터페이스로 Spring/JPA 의존성 없음.
 * 구현체는 Infrastructure Layer에 위치.
 */
public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByTransactionId(String transactionId);

    Optional<Payment> findByOrderId(Long orderId);

    /**
     * 동기화가 필요한 결제 목록 조회 (PENDING, PENDING_RETRY 상태).
     */
    List<Payment> findAllPendingPayments();
}
