package com.loopers.infrastructure.idempotency;

import com.loopers.domain.idempotency.EventLogModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface EventLogJpaRepository extends JpaRepository<EventLogModel, Long> {

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM event_log WHERE handled_at < :cutoff LIMIT :batchSize", nativeQuery = true)
    int deleteOlderThan(LocalDateTime cutoff, int batchSize);
}
