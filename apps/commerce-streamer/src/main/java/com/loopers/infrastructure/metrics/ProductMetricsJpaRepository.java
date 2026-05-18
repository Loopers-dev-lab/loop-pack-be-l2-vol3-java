package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, metric_date, view_count, like_count, sales_count) " +
        "VALUES (:productId, :metricDate, :delta, 0, 0) " +
        "ON DUPLICATE KEY UPDATE view_count = view_count + :delta", nativeQuery = true)
    void upsertViewCount(@Param("productId") Long productId, @Param("metricDate") LocalDate metricDate,
                         @Param("delta") int delta);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, metric_date, view_count, like_count, sales_count) " +
        "VALUES (:productId, :metricDate, 0, :delta, 0) " +
        "ON DUPLICATE KEY UPDATE like_count = like_count + :delta", nativeQuery = true)
    void upsertLikeCount(@Param("productId") Long productId, @Param("metricDate") LocalDate metricDate,
                         @Param("delta") int delta);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, metric_date, view_count, like_count, sales_count) " +
        "VALUES (:productId, :metricDate, 0, 0, :delta) " +
        "ON DUPLICATE KEY UPDATE sales_count = sales_count + :delta", nativeQuery = true)
    void upsertSalesCount(@Param("productId") Long productId, @Param("metricDate") LocalDate metricDate,
                          @Param("delta") int delta);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, metric_date, view_count, like_count, sales_count, latest_price, price_updated_at) " +
        "VALUES (:productId, :metricDate, 0, 0, 0, :price, :occurredAt) " +
        "ON DUPLICATE KEY UPDATE " +
        "latest_price = IF(:occurredAt > COALESCE(price_updated_at, '1970-01-01'), :price, latest_price), " +
        "price_updated_at = IF(:occurredAt > COALESCE(price_updated_at, '1970-01-01'), :occurredAt, price_updated_at)",
        nativeQuery = true)
    void upsertPrice(@Param("productId") Long productId, @Param("metricDate") LocalDate metricDate,
                     @Param("price") BigDecimal price, @Param("occurredAt") Instant occurredAt);
}
