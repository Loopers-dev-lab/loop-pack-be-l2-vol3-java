package com.loopers.infrastructure.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {

    @Query("SELECT o FROM OutboxEventEntity o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC LIMIT :limit")
    List<OutboxEventEntity> findPendingEvents(@Param("limit") int limit);

    @Query("SELECT o FROM OutboxEventEntity o WHERE o.status = 'FAILED' AND o.retryCount < :maxRetry ORDER BY o.createdAt ASC LIMIT :limit")
    List<OutboxEventEntity> findRetryableEvents(@Param("maxRetry") int maxRetry, @Param("limit") int limit);

    @Query("SELECT o FROM OutboxEventEntity o WHERE o.status = 'PUBLISHED' AND o.publishedAt < :before")
    List<OutboxEventEntity> findPublishedBefore(@Param("before") ZonedDateTime before);

    long countByStatus(OutboxStatus status);
}
