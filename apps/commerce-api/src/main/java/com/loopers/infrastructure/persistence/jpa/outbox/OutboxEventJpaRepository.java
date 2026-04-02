package com.loopers.infrastructure.persistence.jpa.outbox;

import com.loopers.domain.outbox.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * OutboxEvent JPA Repository.
 */
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, Long> {

    @Query("SELECT o FROM OutboxEventJpaEntity o WHERE o.status = :status ORDER BY o.createdAt ASC LIMIT :limit")
    List<OutboxEventJpaEntity> findByStatusOrderByCreatedAtAsc(
        @Param("status") OutboxStatus status,
        @Param("limit") int limit
    );

    @Modifying
    @Query("UPDATE OutboxEventJpaEntity o SET o.status = :status WHERE o.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") OutboxStatus status);

    @Modifying
    @Query("UPDATE OutboxEventJpaEntity o SET o.status = 'SENT', o.sentAt = :sentAt WHERE o.id = :id")
    void markAsSent(@Param("id") Long id, @Param("sentAt") Instant sentAt);

    @Modifying
    @Query("UPDATE OutboxEventJpaEntity o SET o.retryCount = o.retryCount + 1 WHERE o.id = :id")
    void incrementRetryCount(@Param("id") Long id);
}
