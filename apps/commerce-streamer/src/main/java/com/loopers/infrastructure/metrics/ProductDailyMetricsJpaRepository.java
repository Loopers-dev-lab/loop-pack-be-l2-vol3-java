package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductDailyMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public interface ProductDailyMetricsJpaRepository extends JpaRepository<ProductDailyMetrics, Long> {

    @Modifying
    @Query(value = "INSERT INTO product_daily_metrics (product_id, metric_date, view_count, like_count, order_amount, updated_at) " +
            "VALUES (:productId, :date, 1, 0, 0, :updatedAt) " +
            "ON DUPLICATE KEY UPDATE view_count = view_count + 1, updated_at = :updatedAt",
            nativeQuery = true)
    void upsertViewCount(@Param("productId") Long productId,
                         @Param("date") LocalDate date,
                         @Param("updatedAt") ZonedDateTime updatedAt);

    @Modifying
    @Query(value = "INSERT INTO product_daily_metrics (product_id, metric_date, view_count, like_count, order_amount, updated_at) " +
            "VALUES (:productId, :date, 0, GREATEST(:delta, 0), 0, :updatedAt) " +
            "ON DUPLICATE KEY UPDATE like_count = GREATEST(like_count + :delta, 0), updated_at = :updatedAt",
            nativeQuery = true)
    void upsertLikeCount(@Param("productId") Long productId,
                         @Param("date") LocalDate date,
                         @Param("delta") int delta,
                         @Param("updatedAt") ZonedDateTime updatedAt);

    @Modifying
    @Query(value = "INSERT INTO product_daily_metrics (product_id, metric_date, view_count, like_count, order_amount, updated_at) " +
            "VALUES (:productId, :date, 0, 0, GREATEST(:amount, 0), :updatedAt) " +
            "ON DUPLICATE KEY UPDATE order_amount = GREATEST(order_amount + :amount, 0), updated_at = :updatedAt",
            nativeQuery = true)
    void upsertOrderAmount(@Param("productId") Long productId,
                           @Param("date") LocalDate date,
                           @Param("amount") long amount,
                           @Param("updatedAt") ZonedDateTime updatedAt);

    List<ProductDailyMetrics> findByMetricDate(LocalDate metricDate);
}
