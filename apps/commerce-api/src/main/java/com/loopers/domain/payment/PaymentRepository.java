package com.loopers.domain.payment;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    /** 해당 주문의 가장 최근 결제 내역 조회 */
    Optional<Payment> findByOrderId(Long orderId);

    Optional<Payment> findByPgTransactionId(String pgTransactionId);

    /** createdAt 기준 이전에 생성된 PENDING 결제 목록 조회 (배치 복구용) */
    List<Payment> findAllPendingBefore(ZonedDateTime createdAt);
}
