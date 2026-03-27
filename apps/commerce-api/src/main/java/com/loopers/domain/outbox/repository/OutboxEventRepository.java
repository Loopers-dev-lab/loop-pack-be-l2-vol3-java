package com.loopers.domain.outbox.repository;

import com.loopers.domain.outbox.model.OutboxEvent;

import java.util.List;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent outboxEvent);

    List<OutboxEvent> findByStatusInit();
}
