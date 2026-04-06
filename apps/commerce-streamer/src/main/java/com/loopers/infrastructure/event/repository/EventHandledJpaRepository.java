package com.loopers.infrastructure.event.repository;

import com.loopers.infrastructure.event.entity.EventHandledEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledJpaRepository extends JpaRepository<EventHandledEntity, String> {
}
