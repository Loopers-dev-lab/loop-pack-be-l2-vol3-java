package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository outboxEventJpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        return OutboxEventEntity.toDomain(outboxEventJpaRepository.save(OutboxEventEntity.toEntity(outboxEvent)));
    }

    @Override
    public List<OutboxEvent> findUnpublished() {
        return outboxEventJpaRepository.findByPublishedAtIsNull().stream()
            .map(OutboxEventEntity::toDomain)
            .toList();
    }

    @Override
    public void markPublished(Long id) {
        outboxEventJpaRepository.markPublished(id, ZonedDateTime.now());
    }
}
