package com.loopers.infrastructure.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<Outbox, Long> {

    @Query("SELECT o FROM Outbox o WHERE o.published = false AND o.createdAt < :cutoff ORDER BY o.createdAt ASC")
    List<Outbox> findUnpublishedBefore(@Param("cutoff") ZonedDateTime cutoff, Pageable pageable);

    @Modifying
    @Query("DELETE FROM Outbox o WHERE o.published = true AND o.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") ZonedDateTime cutoff);
}
