package com.loopers.collector.idempotency;

/**
 * USER/BRAND/CART 등 메트릭 없는 이벤트의 멱등: DB 대신 Redis SETNX로 부하를 줄인다.
 *
 * @return 최초 처리(키 신규 생성)면 true, 이미 처리된 eventId면 false
 */
@FunctionalInterface
public interface LightweightEventIdempotency {

    boolean tryClaimFirstDelivery(String eventId);
}
