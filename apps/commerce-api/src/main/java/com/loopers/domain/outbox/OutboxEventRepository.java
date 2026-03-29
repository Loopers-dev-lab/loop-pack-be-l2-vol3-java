package com.loopers.domain.outbox;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbox 이벤트 도메인 리포지토리 인터페이스.
 */
public interface OutboxEventRepository {

    /**
     * Outbox 이벤트를 저장한다.
     *
     * @param outboxEvent 저장할 Outbox 이벤트
     * @return 저장된 Outbox 이벤트
     */
    OutboxEvent save(OutboxEvent outboxEvent);

    /**
     * ID로 Outbox 이벤트를 조회한다.
     *
     * @param id Outbox 이벤트 ID
     * @return Outbox 이벤트 (없으면 empty)
     */
    Optional<OutboxEvent> findById(UUID id);

    /**
     * Outbox 이벤트를 원자적으로 발행 완료 상태로 갱신한다.
     *
     * <p>INIT 또는 PUBLISH_FAILED 상태인 경우에만 PUBLISHED로 전이한다.</p>
     *
     * @param id Outbox 이벤트 ID
     * @return 갱신 성공 여부
     */
    boolean updateStatusToPublished(UUID id);

    /**
     * 해당 aggregate의 최신 이벤트 버전을 조회한다.
     *
     * @param aggregateId   대상 엔티티 ID
     * @param aggregateType 도메인 타입
     * @return 최신 버전 번호 (이벤트가 없으면 0)
     */
    Long findLatestVersion(Long aggregateId, String aggregateType);

    /**
     * 발행 대기 중인 Outbox 이벤트를 생성 시각 오름차순으로 조회한다.
     *
     * <p>INIT 또는 PUBLISH_FAILED 상태의 이벤트를 반환한다.
     * 재시도 상한 초과 시 DEAD로 전이되므로 PUBLISH_FAILED은 항상 재시도 가능한 건만 포함한다.</p>
     *
     * @param limit 최대 조회 건수
     * @return 발행 대상 Outbox 이벤트 목록
     */
    List<OutboxEvent> findPendingEvents(int limit);
}
