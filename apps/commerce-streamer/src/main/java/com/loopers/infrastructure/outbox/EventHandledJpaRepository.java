package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.EventHandled;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledJpaRepository extends JpaRepository<EventHandled, String> {}
