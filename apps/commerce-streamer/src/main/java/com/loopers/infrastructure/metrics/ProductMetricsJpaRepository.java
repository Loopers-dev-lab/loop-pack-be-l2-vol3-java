package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO product_metrics (product_id, metric_hour, like_count, order_count, view_count)
            VALUES (:productId, :metricHour, :delta, 0, 0)
            ON DUPLICATE KEY UPDATE like_count = like_count + :delta
            """, nativeQuery = true)
    void upsertLike(@Param("productId") Long productId, @Param("delta") int delta, @Param("metricHour") LocalDateTime metricHour);

    @Modifying
    @Query(value = """
            INSERT INTO product_metrics (product_id, metric_hour, like_count, order_count, view_count)
            VALUES (:productId, :metricHour, 0, :quantity, 0)
            ON DUPLICATE KEY UPDATE order_count = order_count + :quantity
            """, nativeQuery = true)
    void upsertOrder(@Param("productId") Long productId, @Param("quantity") long quantity, @Param("metricHour") LocalDateTime metricHour);

    @Modifying
    @Query(value = """
            INSERT INTO product_metrics (product_id, metric_hour, like_count, order_count, view_count)
            VALUES (:productId, :metricHour, 0, 0, 1)
            ON DUPLICATE KEY UPDATE view_count = view_count + 1
            """, nativeQuery = true)
    void upsertView(@Param("productId") Long productId, @Param("metricHour") LocalDateTime metricHour);
}
