package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEventModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventModel, Long> {

    /**
     * PENDING/FAILED 상태이고 next_retry_at이 현재 UTC 시각 이하인 이벤트를 조회한다.
     * UTC_TIMESTAMP()를 사용하여 hibernate.jdbc.time_zone=UTC 설정과 일치시킨다.
     * Java datetime 파라미터를 사용하지 않아 NORMALIZE_UTC 타입 불일치를 회피한다.
     */
    @Query(value = """
        SELECT * FROM outbox_event
        WHERE status IN ('PENDING', 'FAILED')
          AND (next_retry_at IS NULL OR next_retry_at <= UTC_TIMESTAMP())
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
