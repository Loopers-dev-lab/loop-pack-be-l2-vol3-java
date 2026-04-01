package com.loopers.infrastructure.event;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledJpaRepository extends JpaRepository<EventHandledEntity, Long> {

    boolean existsByEventId(String eventId);

    int deleteByEventId(String eventId);
}
