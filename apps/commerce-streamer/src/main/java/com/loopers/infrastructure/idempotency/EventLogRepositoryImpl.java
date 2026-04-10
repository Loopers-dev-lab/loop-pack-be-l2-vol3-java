package com.loopers.infrastructure.idempotency;

import com.loopers.domain.idempotency.EventLogModel;
import com.loopers.domain.idempotency.EventLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class EventLogRepositoryImpl implements EventLogRepository {

    private final EventLogJpaRepository jpaRepository;

    @Override
    public EventLogModel save(EventLogModel model) {
        return jpaRepository.save(model);
    }

    @Override
    public int deleteOlderThan(LocalDateTime cutoff, int batchSize) {
        return jpaRepository.deleteOlderThan(cutoff, batchSize);
    }
}
