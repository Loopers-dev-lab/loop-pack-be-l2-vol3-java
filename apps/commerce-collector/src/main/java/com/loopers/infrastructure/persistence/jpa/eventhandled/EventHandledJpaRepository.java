package com.loopers.infrastructure.persistence.jpa.eventhandled;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * EventHandled JPA Repository.
 */
public interface EventHandledJpaRepository extends JpaRepository<EventHandledJpaEntity, Long> {

    boolean existsByEventId(String eventId);
}
