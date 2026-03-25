package com.loopers.infrastructure.eventhandled;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledJpaRepository extends JpaRepository<EventHandled, String> {
}
