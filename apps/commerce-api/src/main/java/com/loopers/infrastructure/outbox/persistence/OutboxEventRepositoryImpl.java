package com.loopers.infrastructure.outbox.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@link OutboxEventRepository}의 인프라스트럭처 구현체.
 *
 * <p>{@link OutboxEventJpaRepository}에 위임하여 Outbox 이벤트 영속성을 처리한다.</p>
 */
@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository outboxEventJpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        return outboxEventJpaRepository.save(outboxEvent);
    }

    @Override
    public Optional<OutboxEvent> findById(Long id) {
        return outboxEventJpaRepository.findById(id);
    }

    @Override
    public Long findLatestVersion(Long aggregateId, String aggregateType) {
        return outboxEventJpaRepository
                .findTopByAggregateIdAndAggregateTypeOrderByVersionDesc(aggregateId, aggregateType)
                .map(OutboxEvent::getVersion)
                .orElse(0L);
    }

    @Override
    public List<OutboxEvent> findPendingEvents(int limit) {
        return outboxEventJpaRepository.findPendingEvents(PageRequest.of(0, limit));
    }
}
