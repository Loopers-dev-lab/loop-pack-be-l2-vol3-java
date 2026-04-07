package com.loopers.domain.queue;

/**
 * Redis 장애 시 대기열 진입 의도를 Kafka로 넘긴다. 구현체는 infrastructure에 둔다.
 */
@FunctionalInterface
public interface QueueJoinFallbackPublisher {

    void publish(String eventId, Long userId, long score, String requestId);
}
