package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {
    Optional<OutboxEventEntity> findByEventId(UUID eventId);

    @Query("""
            select o from OutboxEventEntity o
            where (o.status = :pendingStatus or o.status = :failedStatus)
              and o.nextAttemptAt <= :now
            order by o.id asc
            """)
    List<OutboxEventEntity> findPublishCandidates(
            @Param("pendingStatus") OutboxEventStatus pendingStatus,
            @Param("failedStatus") OutboxEventStatus failedStatus,
            @Param("now") ZonedDateTime now
    );
}
