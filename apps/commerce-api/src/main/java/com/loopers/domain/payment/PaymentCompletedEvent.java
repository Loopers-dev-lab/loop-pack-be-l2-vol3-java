package com.loopers.domain.payment;

/**
 * 결제 완료 이벤트.
 * PaymentResultHandler에서 결제 성공 콜백 처리 후 발행하며,
 * AFTER_COMMIT 시점에 리스너가 수신하여 알림/로깅 등 부가 로직을 처리한다.
 */
public record PaymentCompletedEvent(
        Long paymentId,
        Long orderId,
        Long userId,
        int amount,
        String transactionKey
) {}
