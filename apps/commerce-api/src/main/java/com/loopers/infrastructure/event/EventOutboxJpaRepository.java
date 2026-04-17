package com.loopers.infrastructure.event;

import com.loopers.domain.event.EventOutbox;
import com.loopers.domain.event.EventOutboxRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventOutboxJpaRepository extends JpaRepository<EventOutbox, Long>, EventOutboxRepository {
}
