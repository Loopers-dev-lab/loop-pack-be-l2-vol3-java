package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEventModel;
import com.loopers.domain.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;

    @Override
    public OutboxEventModel save(OutboxEventModel model) {
        return jpaRepository.save(model);
    }

    @Override
    public List<OutboxEventModel> findPendingEvents(int limit) {
        return jpaRepository.findPendingEvents(limit);
    }

    @Override
    public int deletePublishedOlderThan(LocalDateTime cutoff, int batchSize) {
        return jpaRepository.deletePublishedOlderThan(cutoff, batchSize);
    }

    @Override
    public int deleteDeadOlderThan(LocalDateTime cutoff, int batchSize) {
        return jpaRepository.deleteDeadOlderThan(cutoff, batchSize);
    }
}
