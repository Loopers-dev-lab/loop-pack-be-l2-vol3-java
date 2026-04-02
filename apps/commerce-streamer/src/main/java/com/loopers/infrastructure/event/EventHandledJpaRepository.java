package com.loopers.infrastructure.event;

import java.time.ZonedDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledJpaRepository extends JpaRepository<EventHandledEntity, Long> {

    boolean existsByEventIdAndEventType(String eventId, String eventType);

    boolean existsByEntityIdAndEventTypeAndOccurredAtGreaterThanEqual(String entityId, String eventType, ZonedDateTime occurredAt);
}
