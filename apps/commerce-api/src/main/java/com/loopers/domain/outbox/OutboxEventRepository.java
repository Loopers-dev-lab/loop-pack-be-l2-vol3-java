package com.loopers.domain.outbox;

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
     * 해당 aggregate의 최신 이벤트 버전을 조회한다.
     *
     * @param aggregateId   대상 엔티티 ID
     * @param aggregateType 도메인 타입
     * @return 최신 버전 번호 (이벤트가 없으면 0)
     */
    Long findLatestVersion(Long aggregateId, String aggregateType);
}
