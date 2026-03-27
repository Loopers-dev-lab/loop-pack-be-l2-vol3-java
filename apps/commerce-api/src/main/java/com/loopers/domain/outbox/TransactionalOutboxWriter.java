package com.loopers.domain.outbox;

import java.util.Map;
import java.util.UUID;

/**
 * 동일 DB 트랜잭션 안에 Kafka 발행 의도를 outbox 테이블에 기록한다.
 * 실제 브로커 전송은 별도 릴레이(commerce-batch 등)가 담당한다.
 */
public interface TransactionalOutboxWriter {

    /**
     * @param eventId outbox 이벤트 ID(예: 쿠폰 발급 요청의 requestId와 동일하게 두어 멱등·추적에 사용)
     */
    void record(String eventId, String topic, String partitionKey, String eventType, Map<String, ?> payload);

    default void record(String topic, String partitionKey, String eventType, Map<String, ?> payload) {
        record(UUID.randomUUID().toString(), topic, partitionKey, eventType, payload);
    }
}
