package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEventModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventModel, Long> {

    @Query(value = """
        SELECT * FROM outbox_event
        WHERE status IN ('PENDING', 'FAILED')
          AND (next_retry_at IS NULL OR next_retry_at <= NOW())
        ORDER BY created_at ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<OutboxEventModel> findPendingEvents(int limit);

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM outbox_event
        WHERE status = 'PUBLISHED' AND published_at < :cutoff
        LIMIT :batchSize
        """, nativeQuery = true)
    int deletePublishedOlderThan(LocalDateTime cutoff, int batchSize);

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM outbox_event
        WHERE status = 'DEAD' AND created_at < :cutoff
        LIMIT :batchSize
        """, nativeQuery = true)
    int deleteDeadOlderThan(LocalDateTime cutoff, int batchSize);
}
