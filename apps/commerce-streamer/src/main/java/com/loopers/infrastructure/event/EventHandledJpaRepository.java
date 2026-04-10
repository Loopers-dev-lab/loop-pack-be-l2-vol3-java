package com.loopers.infrastructure.event;

import com.loopers.domain.event.EventHandledEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledJpaRepository extends JpaRepository<EventHandledEntity, String> {
}
