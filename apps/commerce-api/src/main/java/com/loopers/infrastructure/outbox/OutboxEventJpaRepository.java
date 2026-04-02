package com.loopers.infrastructure.outbox;

import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, Long> {

    List<OutboxEventEntity> findByPublishedAtIsNull();

    @Modifying
    @Query("UPDATE OutboxEventEntity e SET e.publishedAt = :publishedAt WHERE e.id = :id")
    void markPublished(@Param("id") Long id, @Param("publishedAt") ZonedDateTime publishedAt);
}
