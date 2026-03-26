package com.loopers.infrastructure.collector;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsModel, Long> {

    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO product_metrics (product_id, like_count, last_event_occurred_at, updated_at)
            VALUES (:productId, :delta, :occurredAt, NOW(6))
            ON DUPLICATE KEY UPDATE
                like_count = IF(last_event_occurred_at IS NULL OR last_event_occurred_at < VALUES(last_event_occurred_at),
                                like_count + VALUES(like_count),
                                like_count),
                last_event_occurred_at = IF(last_event_occurred_at IS NULL OR last_event_occurred_at < VALUES(last_event_occurred_at),
                                            VALUES(last_event_occurred_at),
                                            last_event_occurred_at),
                updated_at = IF(last_event_occurred_at IS NULL OR last_event_occurred_at < VALUES(last_event_occurred_at),
                                NOW(6),
                                updated_at)
            """, nativeQuery = true)
    int applyLikeDeltaIfNewer(@Param("productId") Long productId, @Param("delta") long delta, @Param("occurredAt") Instant occurredAt);
}
