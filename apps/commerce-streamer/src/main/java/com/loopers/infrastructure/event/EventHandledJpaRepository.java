package com.loopers.infrastructure.event;

import com.loopers.domain.event.EventHandled;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

public interface EventHandledJpaRepository extends JpaRepository<EventHandled, Long> {
    boolean existsByEventId(String eventId);

    @Query("SELECT e.eventId FROM EventHandled e WHERE e.eventId IN :eventIds")
    List<String> findEventIdsByEventIdIn(@Param("eventIds") Collection<String> eventIds);

    @Modifying
    @Query(value = "INSERT IGNORE INTO event_handled (event_id, occurred_at, handled_at) " +
            "VALUES (:eventId, :occurredAt, NOW())", nativeQuery = true)
    int insertIgnore(@Param("eventId") String eventId, @Param("occurredAt") ZonedDateTime occurredAt);

    @Modifying
    @Query("DELETE FROM EventHandled e WHERE e.handledAt < :cutoff")
    int deleteHandledBefore(@Param("cutoff") ZonedDateTime cutoff);
}
