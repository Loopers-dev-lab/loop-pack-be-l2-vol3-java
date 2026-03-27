package com.loopers.infrastructure.outbox;

import com.loopers.support.outbox.OutboxEvent;
import com.loopers.support.outbox.OutboxEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusOrderByIdAsc(OutboxEventStatus status, Pageable pageable);

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status AND o.createdAt < :threshold ORDER BY o.id ASC")
    List<OutboxEvent> findByStatusAndCreatedAtBeforeOrderByIdAsc(
            @Param("status") OutboxEventStatus status,
            @Param("threshold") ZonedDateTime threshold,
            Pageable pageable);

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = com.loopers.support.outbox.OutboxEventStatus.PENDING " +
            "AND (o.nextRetryAt IS NULL OR o.nextRetryAt <= CURRENT_TIMESTAMP) ORDER BY o.id ASC")
    List<OutboxEvent> findRetryableEvents(Pageable pageable);

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = 'SENT', o.sentAt = CURRENT_TIMESTAMP WHERE o.eventId = :eventId AND o.status = 'PENDING'")
    int markPublishedByEventId(@Param("eventId") String eventId);

    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.status = :status AND o.sentAt < :before")
    int deleteByStatusAndSentAtBefore(@Param("status") OutboxEventStatus status,
                                      @Param("before") ZonedDateTime before);
}
