package com.loopers.infrastructure.outbox.repository.impl;

import com.loopers.domain.outbox.model.OutboxEvent;
import com.loopers.domain.outbox.model.OutboxStatus;
import com.loopers.domain.outbox.repository.OutboxEventRepository;
import com.loopers.infrastructure.outbox.entity.OutboxEventEntity;
import com.loopers.infrastructure.outbox.repository.OutboxEventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository outboxEventJpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        OutboxEventEntity entity = outboxEventJpaRepository.save(OutboxEventEntity.toEntity(outboxEvent));
        return entity.toModel();
    }

    @Override
    public List<OutboxEvent> findByStatusInit() {
        return outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.INIT).stream()
                .map(OutboxEventEntity::toModel)
                .toList();
    }
}
