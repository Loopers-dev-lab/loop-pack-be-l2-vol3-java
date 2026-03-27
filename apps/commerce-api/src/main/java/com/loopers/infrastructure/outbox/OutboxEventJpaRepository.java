package com.loopers.infrastructure.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {

    @Query("SELECT o FROM OutboxEventEntity o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC LIMIT :limit")
    List<OutboxEventEntity> findPendingEvents(@Param("limit") int limit);

    /**
     * PENDING 이벤트를 조회하고 즉시 락을 획득한다 (FOR UPDATE SKIP LOCKED)
     * 멀티 인스턴스 환경에서 중복 발행을 방지한다.
     */
    @Query(value = """
        SELECT * FROM outbox_event
        WHERE status = 'PENDING'
        ORDER BY created_at ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEventEntity> findPendingEventsForUpdate(@Param("limit") int limit);

    @Query("SELECT o FROM OutboxEventEntity o WHERE o.status = 'PROCESSING' ORDER BY o.createdAt ASC LIMIT :limit")
    List<OutboxEventEntity> findProcessingEvents(@Param("limit") int limit);

    @Query("SELECT o FROM OutboxEventEntity o WHERE o.status = 'FAILED' AND o.retryCount < :maxRetry ORDER BY o.createdAt ASC LIMIT :limit")
    List<OutboxEventEntity> findRetryableEvents(@Param("maxRetry") int maxRetry, @Param("limit") int limit);

    @Query("SELECT o FROM OutboxEventEntity o WHERE o.status = 'PUBLISHED' AND o.publishedAt < :before")
    List<OutboxEventEntity> findPublishedBefore(@Param("before") ZonedDateTime before);

    @Query("SELECT o FROM OutboxEventEntity o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC LIMIT 1")
    Optional<OutboxEventEntity> findOldestPendingEvent();

    @Query("SELECT o FROM OutboxEventEntity o WHERE o.status = 'PROCESSING' AND o.updatedAt < :threshold ORDER BY o.updatedAt ASC")
    List<OutboxEventEntity> findStalledProcessingEvents(@Param("threshold") ZonedDateTime threshold);

    long countByStatus(OutboxStatus status);
}
