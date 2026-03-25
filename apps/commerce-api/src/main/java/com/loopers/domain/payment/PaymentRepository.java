package com.loopers.domain.payment;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(Long id);

    Optional<Payment> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    Optional<Payment> findByTransactionKey(String transactionKey);

    // 폴링 스케줄러용: 특정 상태이면서 일정 시간 이전에 생성된 결제 목록 조회
    List<Payment> findAllByStatusAndCreatedAtBefore(PaymentStatus status, ZonedDateTime before);

    // 조건부 상태 전이: PENDING인 경우에만 UPDATE (rows affected = 0이면 이미 처리된 건)
    // WHERE status = 'PENDING'이 DB 레벨의 원자적 멱등성 보장 → @Version 불필요
    boolean markSuccessIfPending(Long id, String transactionKey);

    boolean markFailedIfPending(Long id, String transactionKey, String failureReason);

    boolean markTimeoutIfPending(Long id);
}
