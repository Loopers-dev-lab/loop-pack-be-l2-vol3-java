package com.loopers.infrastructure.event;

import com.loopers.domain.event.EventHandledModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledJpaRepository extends JpaRepository<EventHandledModel, Long> {
    boolean existsByEventId(String eventId);
}
