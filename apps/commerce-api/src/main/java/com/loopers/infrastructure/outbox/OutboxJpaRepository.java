package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatus(OutboxStatus status, Pageable pageable);
}
