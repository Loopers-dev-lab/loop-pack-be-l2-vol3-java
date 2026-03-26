package com.loopers.domain.outbox;

import java.util.Map;

/**
 * 동일 DB 트랜잭션 안에 Kafka 발행 의도를 outbox 테이블에 기록한다.
 * 실제 브로커 전송은 별도 릴레이(commerce-batch 등)가 담당한다.
 */
public interface TransactionalOutboxWriter {

    void record(String topic, String partitionKey, String eventType, Map<String, ?> payload);
}
