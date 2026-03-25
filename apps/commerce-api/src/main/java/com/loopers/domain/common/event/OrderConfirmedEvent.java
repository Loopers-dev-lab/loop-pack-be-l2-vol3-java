package com.loopers.domain.common.event;

/**
 * 주문 확정 이벤트 — "주문이 결제 완료되어 확정됐다"
 *
 * 발행 시점: TX2 커밋 이후 (AFTER_COMMIT)
 * 소비자: 포인트 적립 (현재 ApplicationEvent, 추후 Kafka 전환)
 *
 * 같은 TX에 묶인 것들 (이벤트로 분리 ❌):
 *   결제 승인, 재고 커밋, 주문 상태 PAID — TX2에서 원자적 처리
 *
 * 이벤트로 분리한 것 (✅):
 *   포인트 적립 — 실패해도 CS 보정 가능, 금전 가치이므로 추후 Kafka
 */
public record OrderConfirmedEvent(
        Long orderId,
        Long userId,
        int totalAmount,
        Long paymentId
) {
}
