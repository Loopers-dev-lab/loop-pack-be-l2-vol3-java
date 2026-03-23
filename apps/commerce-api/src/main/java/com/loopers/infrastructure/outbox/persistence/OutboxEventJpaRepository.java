package com.loopers.infrastructure.outbox.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.loopers.domain.outbox.OutboxEvent;

/**
 * Outbox 이벤트 JPA 리포지토리.
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * 해당 aggregate의 최신 이벤트를 조회한다.
     */
    Optional<OutboxEvent> findTopByAggregateIdAndAggregateTypeOrderByVersionDesc(
            Long aggregateId, String aggregateType);
}
