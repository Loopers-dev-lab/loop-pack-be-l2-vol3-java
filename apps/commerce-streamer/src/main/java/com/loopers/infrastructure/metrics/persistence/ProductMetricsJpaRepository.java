package com.loopers.infrastructure.metrics.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.loopers.domain.metrics.ProductMetrics;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, metric_date, like_count, order_count, view_count, updated_at) "
            + "VALUES (:productId, CURDATE(), GREATEST(:delta, 0), 0, 0, NOW()) "
            + "ON DUPLICATE KEY UPDATE "
            + "like_count = GREATEST(like_count + :delta, 0), updated_at = NOW()",
            nativeQuery = true)
    void upsertLikeCount(@Param("productId") Long productId, @Param("delta") Long delta);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, metric_date, like_count, order_count, view_count, updated_at) "
            + "VALUES (:productId, CURDATE(), 0, :quantity, 0, NOW()) "
            + "ON DUPLICATE KEY UPDATE "
            + "order_count = order_count + :quantity, updated_at = NOW()",
            nativeQuery = true)
    void upsertOrderCount(@Param("productId") Long productId, @Param("quantity") Long quantity);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, metric_date, like_count, order_count, view_count, updated_at) "
            + "VALUES (:productId, CURDATE(), 0, 0, 1, NOW()) "
            + "ON DUPLICATE KEY UPDATE "
            + "view_count = view_count + 1, updated_at = NOW()",
            nativeQuery = true)
    void upsertViewCount(@Param("productId") Long productId);
}
