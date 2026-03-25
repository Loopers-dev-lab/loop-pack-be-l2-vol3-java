package com.loopers.domain.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.domain.shared.annotation.DomainService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

/**
 * Outbox 이벤트의 도메인 서비스.
 *
 * <p>Outbox 이벤트의 생성, 조회, 상태 변경을 담당한다.</p>
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
     * @param eventId       이벤트 식별자
     * @param aggregateId   대상 엔티티 ID
     * @param aggregateType 도메인 타입 (예: "LIKE", "ORDER")
     * @param eventType     이벤트 종류 (예: "LIKED", "ORDER_PLACED")
     * @param payload       직렬화된 이벤트 데이터 (JSON)
     * @param topic         발행 대상 Kafka 토픽
     * @param partitionKey  Kafka 파티션 키
     */
    @Transactional
    public void save(
            UUID eventId,
            Long aggregateId,
            String aggregateType,
            String eventType,
            String payload,
            String topic,
            String partitionKey
    ) {
        Long latestVersion = outboxEventRepository.findLatestVersion(aggregateId, aggregateType);
        OutboxEvent outboxEvent = OutboxEvent.create(
                eventId,
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

    /**
     * ID로 Outbox 이벤트를 조회한다.
     *
     * @param eventId Outbox 이벤트 ID
     * @return Outbox 이벤트
     * @throws CoreException 이벤트가 존재하지 않는 경우
     */
    public OutboxEvent findById(UUID eventId) {
        return outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND));
    }

    /**
     * 발행 대기 중인 Outbox 이벤트를 조회한다.
     *
     * <p>INIT 또는 PUBLISH_FAILED 상태의 이벤트를 반환한다.</p>
     *
     * @param limit 최대 조회 건수
     * @return 발행 대상 Outbox 이벤트 목록
     */
    public List<OutboxEvent> findPendingEvents(int limit) {
        return outboxEventRepository.findPendingEvents(limit);
    }

    /**
     * Outbox 이벤트를 원자적으로 발행 완료 상태로 갱신한다.
     *
     * <p>INIT 또는 PUBLISH_FAILED 상태일 때만 PUBLISHED로 전이하며,
     * 이미 다른 프로세스가 처리한 경우 false를 반환한다.</p>
     *
     * @param eventId Outbox 이벤트 ID
     * @return 발행 성공 여부
     */
    @Transactional
    public boolean publish(UUID eventId) {
        return outboxEventRepository.updateStatusToPublished(eventId);
    }

    /**
     * Outbox 이벤트를 발행 실패 상태로 갱신한다.
     *
     * @param eventId Outbox 이벤트 ID
     * @return 상태가 갱신된 Outbox 이벤트
     */
    @Transactional
    public OutboxEvent publishFail(UUID eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND));
        event.publishFail();
        return event;
    }

    /**
     * Outbox 이벤트를 즉시 DEAD 상태로 갱신한다.
     *
     * <p>재시도 불가능한 실패(직렬화 오류, 메시지 크기 초과 등)에 사용한다.</p>
     *
     * @param eventId Outbox 이벤트 ID
     */
    @Transactional
    public void dead(UUID eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND));
        event.dead();
    }
}
