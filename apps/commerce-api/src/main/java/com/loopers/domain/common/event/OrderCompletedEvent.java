package com.loopers.domain.common.event;

/**
 * 주문 완료 이벤트 — "주문 생성 + 결제 확정이 모두 끝났다"
 *
 * 발행 시점: OrderFacade.createOrder() / createOrderFromCart() 완료 직후
 * 소비자: 대기열 토큰 정리 (QueueTokenCleanupListener)
 *
 * OrderConfirmedEvent(TX2 커밋 후 포인트 적립)와 구분:
 *   - OrderConfirmedEvent: TX2 내부에서 발행, Outbox 패턴, 포인트 적립용
 *   - OrderCompletedEvent: 전체 주문 흐름 완료 후 발행, best-effort 부가 작업용
 *
 * 토큰 삭제는 핵심 비즈니스가 아니므로 @EventListener + best-effort로 충분.
 * 실패해도 TTL(180초)로 자연 만료된다.
 */
public record OrderCompletedEvent(
        Long userId
) {
}
