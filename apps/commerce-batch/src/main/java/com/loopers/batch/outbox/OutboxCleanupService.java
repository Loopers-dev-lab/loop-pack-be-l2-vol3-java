package com.loopers.batch.outbox;

import com.loopers.infrastructure.outbox.OutboxJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class OutboxCleanupService {

    private final OutboxJpaRepository outboxJpaRepository;

    public OutboxCleanupService(OutboxJpaRepository outboxJpaRepository) {
        this.outboxJpaRepository = outboxJpaRepository;
    }

    /**
     * 발행 완료(published)이면서 {@code published_at}이 cutoff 이전인 행을 최대 limit건 삭제한다.
     */
    @Transactional
    public int deletePublishedOlderThan(Instant cutoff, int limit) {
        return outboxJpaRepository.deletePublishedBefore(cutoff, limit);
    }
}
