package com.loopers.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxEventModel, Long> {

    @Query(value = """
            SELECT *
            FROM outbox_event
            WHERE published = 0
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventModel> findPendingForUpdateSkipLocked(@Param("limit") int limit);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            DELETE FROM outbox_event
            WHERE published = 1
              AND published_at IS NOT NULL
              AND published_at < :cutoff
            ORDER BY id ASC
            LIMIT :limit
            """, nativeQuery = true)
    int deletePublishedBefore(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
}

