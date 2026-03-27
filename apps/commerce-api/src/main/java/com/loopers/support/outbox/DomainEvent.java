package com.loopers.support.outbox;

/**
 * Outbox 대상 이벤트의 마커 인터페이스.
 * 이 인터페이스를 구현한 이벤트만 OutboxEventListener가 BEFORE_COMMIT에서 Outbox INSERT한다.
 * ProductViewedEvent 등 유실 허용 이벤트는 이 인터페이스를 구현하지 않는다.
 */
public interface DomainEvent {
}
