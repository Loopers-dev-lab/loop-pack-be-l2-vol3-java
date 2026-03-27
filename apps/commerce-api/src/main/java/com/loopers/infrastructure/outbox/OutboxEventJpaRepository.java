package com.loopers.infrastructure.outbox;

import com.loopers.support.outbox.OutboxEvent;
import com.loopers.support.outbox.OutboxEventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status AND o.createdAt < :before ORDER BY o.id ASC")
    List<OutboxEvent> findStalePending(@Param("status") OutboxEventStatus status,
                                       @Param("before") ZonedDateTime before,
                                       Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE OutboxEvent o SET o.status = 'SENT', o.sentAt = CURRENT_TIMESTAMP WHERE o.eventId = :eventId AND o.status = 'PENDING'")
    int markPublishedByEventId(@Param("eventId") String eventId);

    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.status = :status AND o.sentAt < :before")
    int deleteByStatusAndSentAtBefore(@Param("status") OutboxEventStatus status,
                                      @Param("before") ZonedDateTime before);
}
