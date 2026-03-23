package com.loopers.domain.outbox;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.shared.annotation.DomainService;

import lombok.RequiredArgsConstructor;

/**
 * Outbox 이벤트의 도메인 서비스.
 *
 * <p>Outbox 이벤트의 생성과 저장을 담당한다.</p>
 */
@DomainService
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;

    /**
     * Outbox 이벤트를 생성하고 저장한다.
     *
     * <p>해당 aggregate의 최신 버전을 조회하여 +1한 버전으로 이벤트를 생성한다.</p>
     *
     * @param aggregateId   대상 엔티티 ID
     * @param aggregateType 도메인 타입 (예: "LIKE", "ORDER")
     * @param eventType     이벤트 종류 (예: "LIKED", "ORDER_PLACED")
     * @param payload       직렬화된 이벤트 데이터 (JSON)
     * @param topic         발행 대상 Kafka 토픽
     * @param partitionKey  Kafka 파티션 키
     */
    @Transactional
    public void save(
            Long aggregateId,
            String aggregateType,
            String eventType,
            String payload,
            String topic,
            String partitionKey
    ) {
        Long latestVersion = outboxEventRepository.findLatestVersion(aggregateId, aggregateType);
        OutboxEvent outboxEvent = OutboxEvent.create(
                aggregateId,
                aggregateType,
                eventType,
                payload,
                topic,
                partitionKey,
                latestVersion + 1
        );
        outboxEventRepository.save(outboxEvent);
    }
}
