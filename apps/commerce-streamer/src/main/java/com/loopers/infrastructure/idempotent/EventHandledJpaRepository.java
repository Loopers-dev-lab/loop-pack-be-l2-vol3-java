package com.loopers.infrastructure.idempotent;

import com.loopers.domain.idempotent.EventHandled;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;

public interface EventHandledJpaRepository extends JpaRepository<EventHandled, String> {

    @Modifying
    @Query("DELETE FROM EventHandled e WHERE e.handledAt < :before")
    int deleteByHandledAtBefore(@Param("before") ZonedDateTime before);
}
