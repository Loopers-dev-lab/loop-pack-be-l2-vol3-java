package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository outboxEventJpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        return outboxEventJpaRepository.save(outboxEvent);
    }

    @Override
    public List<OutboxEvent> findAllByStatusOrderByCreatedAtAsc(OutboxStatus status, int limit) {
        return outboxEventJpaRepository.findTop50ByStatusOrderByCreatedAtAsc(status).stream()
                .limit(limit)
                .toList();
    }
}
