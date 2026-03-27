package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, sales_count, view_count, updated_at) VALUES (:productId, 1, 0, 0, :occurredAt) ON DUPLICATE KEY UPDATE like_count = IF(:occurredAt >= updated_at, like_count + 1, like_count), updated_at = IF(:occurredAt >= updated_at, :occurredAt, updated_at)", nativeQuery = true)
    void incrementLikeCount(@Param("productId") Long productId, @Param("occurredAt") ZonedDateTime occurredAt);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, sales_count, view_count, updated_at) VALUES (:productId, 0, 0, 0, :occurredAt) ON DUPLICATE KEY UPDATE like_count = IF(:occurredAt >= updated_at, GREATEST(0, like_count - 1), like_count), updated_at = IF(:occurredAt >= updated_at, :occurredAt, updated_at)", nativeQuery = true)
    void decrementLikeCount(@Param("productId") Long productId, @Param("occurredAt") ZonedDateTime occurredAt);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, sales_count, view_count, updated_at) VALUES (:productId, 0, 1, 0, :occurredAt) ON DUPLICATE KEY UPDATE sales_count = IF(:occurredAt >= updated_at, sales_count + 1, sales_count), updated_at = IF(:occurredAt >= updated_at, :occurredAt, updated_at)", nativeQuery = true)
    void incrementSalesCount(@Param("productId") Long productId, @Param("occurredAt") ZonedDateTime occurredAt);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, sales_count, view_count, updated_at) VALUES (:productId, 0, 0, 1, :occurredAt) ON DUPLICATE KEY UPDATE view_count = IF(:occurredAt >= updated_at, view_count + 1, view_count), updated_at = IF(:occurredAt >= updated_at, :occurredAt, updated_at)", nativeQuery = true)
    void incrementViewCount(@Param("productId") Long productId, @Param("occurredAt") ZonedDateTime occurredAt);
}