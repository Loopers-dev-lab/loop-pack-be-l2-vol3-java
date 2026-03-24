package com.loopers.infrastructure.outbox.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.loopers.domain.outbox.OutboxEvent;

/**
 * Outbox 이벤트 JPA 리포지토리.
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * 해당 aggregate의 최신 이벤트를 조회한다.
     */
    Optional<OutboxEvent> findTopByAggregateIdAndAggregateTypeOrderByVersionDesc(Long aggregateId, String aggregateType);

    @Query("SELECT e FROM OutboxEvent e " +
            "WHERE e.status = 'INIT' OR e.status = 'PUBLISH_FAILED' " +
            "ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents(Pageable pageable);
}
