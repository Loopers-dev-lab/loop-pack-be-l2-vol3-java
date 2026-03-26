package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.outbox.OutboxEventStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public class OutboxEventRepositoryImpl implements OutboxEventRepository {

    private final OutboxEventJpaRepository outboxEventJpaRepository;

    public OutboxEventRepositoryImpl(OutboxEventJpaRepository outboxEventJpaRepository) {
        this.outboxEventJpaRepository = outboxEventJpaRepository;
    }

    @Override
    @Transactional
    public OutboxEvent save(OutboxEvent outboxEvent) {
        return outboxEventJpaRepository.save(OutboxEventEntity.from(outboxEvent)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboxEvent> findPublishCandidates(ZonedDateTime now, int limit) {
        List<OutboxEventEntity> candidates = outboxEventJpaRepository.findPublishCandidates(
                OutboxEventStatus.PENDING,
                OutboxEventStatus.FAILED,
                now
        );
        if (candidates.size() > limit) {
            candidates = candidates.subList(0, limit);
        }
        return candidates.stream().map(OutboxEventEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean markProcessing(Long id, ZonedDateTime nextAttemptAt) {
        OutboxEventEntity entity = outboxEventJpaRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "Outbox 이벤트를 찾을 수 없습니다."));
        return entity.markProcessing(nextAttemptAt);
    }

    @Override
    @Transactional
    public void markPublished(Long id, ZonedDateTime publishedAt) {
        OutboxEventEntity entity = outboxEventJpaRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "Outbox 이벤트를 찾을 수 없습니다."));
        entity.markPublished(publishedAt);
    }

    @Override
    @Transactional
    public void markAckedByEventId(java.util.UUID eventId, ZonedDateTime ackedAt) {
        OutboxEventEntity entity = outboxEventJpaRepository.findByEventId(eventId)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "Outbox 이벤트를 찾을 수 없습니다."));
        entity.markAcked(ackedAt);
    }

    @Override
    @Transactional
    public void markFailed(Long id, String reason, ZonedDateTime nextAttemptAt) {
        OutboxEventEntity entity = outboxEventJpaRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "Outbox 이벤트를 찾을 수 없습니다."));
        entity.markFailed(reason, nextAttemptAt);
    }
}
