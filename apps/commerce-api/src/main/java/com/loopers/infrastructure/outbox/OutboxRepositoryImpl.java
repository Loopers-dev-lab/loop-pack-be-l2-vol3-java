package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class OutboxRepositoryImpl implements OutboxRepository {

    private final OutboxJpaRepository outboxJpaRepository;

    @Override
    public OutboxModel save(OutboxModel outbox) {
        return outboxJpaRepository.save(outbox);
    }

    @Override
    public List<OutboxModel> findPendingWithLimit(int limit) {
        return outboxJpaRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public void markAllPublished(List<Long> ids) {
        if (!ids.isEmpty()) {
            outboxJpaRepository.markAllPublished(ids);
        }
    }
}
