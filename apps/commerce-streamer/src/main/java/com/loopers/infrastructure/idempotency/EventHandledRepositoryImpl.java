package com.loopers.infrastructure.idempotency;

import com.loopers.domain.idempotency.EventHandledModel;
import com.loopers.domain.idempotency.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class EventHandledRepositoryImpl implements EventHandledRepository {

    private final EventHandledJpaRepository jpaRepository;

    @Override
    public boolean existsById(Long eventId) {
        return jpaRepository.existsById(eventId);
    }

    @Override
    public EventHandledModel save(EventHandledModel model) {
        return jpaRepository.save(model);
    }

    @Override
    public int deleteOlderThan(LocalDateTime cutoff, int batchSize) {
        return jpaRepository.deleteOlderThan(cutoff, batchSize);
    }

    @Override
    public Set<Long> findExistingIds(List<Long> eventIds) {
        if (eventIds.isEmpty()) return Set.of();
        return jpaRepository.findExistingIds(eventIds);
    }
}
