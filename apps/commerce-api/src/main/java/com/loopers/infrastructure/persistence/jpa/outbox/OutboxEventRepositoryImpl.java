package com.loopers.infrastructure.persistence.jpa.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * OutboxEventRepository 구현체.
 */
@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent event) {
        OutboxEventJpaEntity entity = OutboxEventMapper.toJpaEntity(event);
        OutboxEventJpaEntity saved = jpaRepository.save(entity);
        return OutboxEventMapper.toDomain(saved);
    }

    @Override
    public List<OutboxEvent> findPendingEvents(int limit) {
        return jpaRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, limit)
            .stream()
            .map(OutboxEventMapper::toDomain)
            .toList();
    }

    @Override
    public void updateStatus(Long id, OutboxStatus status) {
        jpaRepository.updateStatus(id, status);
    }

    @Override
    public void markAsSent(Long id) {
        jpaRepository.markAsSent(id, Instant.now());
    }

    @Override
    public void incrementRetryCount(Long id) {
        jpaRepository.incrementRetryCount(id);
    }
}
