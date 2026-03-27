package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsModel, Long> {

    Optional<ProductMetricsModel> findByRefProductId(Long refProductId);

    @Modifying
    @Query(value = """
        INSERT INTO product_metrics (ref_product_id, like_count, last_event_at, created_at, updated_at)
        VALUES (:refProductId, GREATEST(0, :delta), :eventAt, NOW(6), NOW(6))
        ON DUPLICATE KEY UPDATE
            like_count    = IF(:eventAt > last_event_at OR last_event_at IS NULL,
                               GREATEST(0, like_count + :delta),
                               like_count),
            last_event_at = IF(:eventAt > last_event_at OR last_event_at IS NULL,
                               :eventAt,
                               last_event_at),
            updated_at    = NOW(6)
        """, nativeQuery = true)
    void upsertLikeDelta(@Param("refProductId") Long refProductId,
                         @Param("delta") int delta,
                         @Param("eventAt") LocalDateTime eventAt);
}
