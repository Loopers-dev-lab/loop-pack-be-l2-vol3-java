package com.loopers.domain.outbox;

import org.springframework.data.domain.Pageable;

import java.util.List;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent outboxEvent);

    List<OutboxEvent> findAllByStatus(OutboxStatus status, Pageable pageable);
}