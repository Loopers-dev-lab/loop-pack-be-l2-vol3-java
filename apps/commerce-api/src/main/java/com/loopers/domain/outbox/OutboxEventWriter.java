package com.loopers.domain.outbox;

/**
 * 도메인 이벤트를 Outbox에 기록하는 인터페이스.
 *
 * <p>도메인 서비스에서 이벤트 발생 시 호출하며,
 * 직렬화와 토픽 매핑은 인프라스트럭처 구현체에서 담당한다.</p>
 */
public interface OutboxEventWriter {

    /**
     * 도메인 이벤트를 Outbox에 기록한다.
     *
     * @param aggregateId   대상 엔티티 ID
     * @param aggregateType 도메인 타입 (예: "LIKE")
     * @param eventType     이벤트 종류 (예: "LIKED")
     * @param event         이벤트 객체
     * @param topic         발행 대상 토픽
     * @param partitionKey  파티션 키
     */
    void write(
            Long aggregateId,
            String aggregateType,
            String eventType,
            Object event,
            String topic,
            String partitionKey
    );
}
