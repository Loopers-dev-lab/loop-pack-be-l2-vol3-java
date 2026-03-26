package com.loopers.infrastructure.idempotency;

import com.loopers.domain.idempotency.EventHandledModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface EventHandledJpaRepository extends JpaRepository<EventHandledModel, Long> {

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM event_handled WHERE handled_at < :cutoff LIMIT :batchSize", nativeQuery = true)
    int deleteOlderThan(LocalDateTime cutoff, int batchSize);

    @Query("SELECT e.eventId FROM EventHandledModel e WHERE e.eventId IN :eventIds")
    Set<Long> findExistingIds(@Param("eventIds") List<Long> eventIds);
}
