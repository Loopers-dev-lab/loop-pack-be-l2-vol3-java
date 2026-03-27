package com.loopers.infrastructure.log;

import com.loopers.domain.log.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;

public interface EventLogJpaRepository extends JpaRepository<EventLog, Long> {

    @Modifying
    @Query("DELETE FROM EventLog e WHERE e.createdAt < :before")
    int deleteByCreatedAtBefore(@Param("before") ZonedDateTime before);
}
