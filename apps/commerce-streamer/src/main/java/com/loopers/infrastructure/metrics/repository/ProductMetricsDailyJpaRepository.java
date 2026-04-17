package com.loopers.infrastructure.metrics.repository;

import com.loopers.infrastructure.metrics.entity.ProductMetricsDailyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface ProductMetricsDailyJpaRepository extends JpaRepository<ProductMetricsDailyEntity, ProductMetricsDailyEntity.ProductMetricsDailyId> {

    @Modifying
    @Query(value = """
        INSERT INTO product_metrics_daily
            (metric_date, product_id, view_count, like_count, order_count, score, created_at, updated_at)
        VALUES (:metricDate, :productId, 1, 0, 0, :scoreDelta, NOW(6), NOW(6))
        ON DUPLICATE KEY UPDATE
            view_count = view_count + 1,
            score = score + :scoreDelta,
            updated_at = NOW(6)
        """, nativeQuery = true)
    void incrementViewCount(@Param("metricDate") LocalDate metricDate,
                            @Param("productId") Long productId,
                            @Param("scoreDelta") double scoreDelta);

    @Modifying
    @Query(value = """
        INSERT INTO product_metrics_daily
            (metric_date, product_id, view_count, like_count, order_count, score, created_at, updated_at)
        VALUES (:metricDate, :productId, 0, 1, 0, :scoreDelta, NOW(6), NOW(6))
        ON DUPLICATE KEY UPDATE
            like_count = like_count + 1,
            score = score + :scoreDelta,
            updated_at = NOW(6)
        """, nativeQuery = true)
    void incrementLikeCount(@Param("metricDate") LocalDate metricDate,
                            @Param("productId") Long productId,
                            @Param("scoreDelta") double scoreDelta);

    @Modifying
    @Query(value = """
        INSERT INTO product_metrics_daily
            (metric_date, product_id, view_count, like_count, order_count, score, created_at, updated_at)
        VALUES (:metricDate, :productId, 0, 0, 0, 0, NOW(6), NOW(6))
        ON DUPLICATE KEY UPDATE
            score = CASE WHEN like_count > 0 THEN score + :scoreDelta ELSE score END,
            like_count = GREATEST(like_count - 1, 0),
            updated_at = NOW(6)
        """, nativeQuery = true)
    void decrementLikeCount(@Param("metricDate") LocalDate metricDate,
                            @Param("productId") Long productId,
                            @Param("scoreDelta") double scoreDelta);

    @Modifying
    @Query(value = """
        INSERT INTO product_metrics_daily
            (metric_date, product_id, view_count, like_count, order_count, score, created_at, updated_at)
        VALUES (:metricDate, :productId, 0, 0, 1, :scoreDelta, NOW(6), NOW(6))
        ON DUPLICATE KEY UPDATE
            order_count = order_count + 1,
            score = score + :scoreDelta,
            updated_at = NOW(6)
        """, nativeQuery = true)
    void incrementOrderCount(@Param("metricDate") LocalDate metricDate,
                             @Param("productId") Long productId,
                             @Param("scoreDelta") double scoreDelta);
}
