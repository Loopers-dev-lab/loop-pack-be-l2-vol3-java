package com.loopers.infrastructure.outbox;

import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxEvent, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")}) // SKIP LOCKED
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC LIMIT :batchSize")
    List<OutboxEvent> findPendingEventsForUpdate(@Param("batchSize") int batchSize);

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PROCESSING' AND e.updatedAt < :threshold")
    List<OutboxEvent> findStalledProcessingEvents(@Param("threshold") ZonedDateTime threshold);

    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'FAILED' AND e.updatedAt < :threshold AND e.retryCount < :maxRetryCount")
    List<OutboxEvent> findRetryableFailedEvents(@Param("threshold") ZonedDateTime threshold, @Param("maxRetryCount") int maxRetryCount);

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = 'PUBLISHED' AND e.publishedAt < :threshold")
    void deletePublishedEventsBefore(@Param("threshold") ZonedDateTime threshold);
}
