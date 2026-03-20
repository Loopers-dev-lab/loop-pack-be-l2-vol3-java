package com.loopers.application.payment;

/**
 * PENDING 저장 트랜잭션 결과. PG 호출용 파라미터와 응답용 Info를 함께 반환.
 */
public record PendingPaymentResult(
        PaymentInfo paymentInfo,
        PaymentRequestParam requestParam
) {
}
