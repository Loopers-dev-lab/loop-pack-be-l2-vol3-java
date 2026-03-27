package com.loopers.infrastructure.collector;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsModel, Long> {

    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO product_metrics (product_id, like_count, view_count, sold_quantity, last_event_occurred_at, updated_at)
            VALUES (:productId, :delta, 0, 0, :occurredAt, NOW(6))
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

    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO product_metrics (product_id, like_count, view_count, sold_quantity, last_event_occurred_at, updated_at)
            VALUES (:productId, 0, :delta, 0, :occurredAt, NOW(6))
            ON DUPLICATE KEY UPDATE
                view_count = IF(last_event_occurred_at IS NULL OR last_event_occurred_at < VALUES(last_event_occurred_at),
                                view_count + VALUES(view_count),
                                view_count),
                last_event_occurred_at = IF(last_event_occurred_at IS NULL OR last_event_occurred_at < VALUES(last_event_occurred_at),
                                            VALUES(last_event_occurred_at),
                                            last_event_occurred_at),
                updated_at = IF(last_event_occurred_at IS NULL OR last_event_occurred_at < VALUES(last_event_occurred_at),
                                NOW(6),
                                updated_at)
            """, nativeQuery = true)
    int applyViewDeltaIfNewer(@Param("productId") Long productId, @Param("delta") long delta, @Param("occurredAt") Instant occurredAt);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO product_metrics (product_id, like_count, view_count, sold_quantity, last_event_occurred_at, updated_at)
            VALUES (:productId, 0, 0, :delta, :occurredAt, NOW(6))
            ON DUPLICATE KEY UPDATE
                sold_quantity = IF(last_event_occurred_at IS NULL OR last_event_occurred_at < VALUES(last_event_occurred_at),
                                sold_quantity + VALUES(sold_quantity),
                                sold_quantity),
                last_event_occurred_at = IF(last_event_occurred_at IS NULL OR last_event_occurred_at < VALUES(last_event_occurred_at),
                                            VALUES(last_event_occurred_at),
                                            last_event_occurred_at),
                updated_at = IF(last_event_occurred_at IS NULL OR last_event_occurred_at < VALUES(last_event_occurred_at),
                                NOW(6),
                                updated_at)
            """, nativeQuery = true)
    int applySoldDeltaIfNewer(@Param("productId") Long productId, @Param("delta") long delta, @Param("occurredAt") Instant occurredAt);
}
