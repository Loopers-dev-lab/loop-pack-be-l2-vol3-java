package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, view_count, sales_count, sales_amount, updated_at) " +
            "VALUES (:productId, :delta, 0, 0, 0, NOW(6)) " +
            "ON DUPLICATE KEY UPDATE like_count = like_count + :delta, updated_at = NOW(6)",
            nativeQuery = true)
    void upsertLikeCount(@Param("productId") Long productId, @Param("delta") long delta);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, view_count, sales_count, sales_amount, updated_at) " +
            "VALUES (:productId, 0, :delta, 0, 0, NOW(6)) " +
            "ON DUPLICATE KEY UPDATE view_count = view_count + :delta, updated_at = NOW(6)",
            nativeQuery = true)
    void upsertViewCount(@Param("productId") Long productId, @Param("delta") long delta);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, view_count, sales_count, sales_amount, updated_at) " +
            "VALUES (:productId, 0, 0, :countDelta, :amountDelta, NOW(6)) " +
            "ON DUPLICATE KEY UPDATE sales_count = sales_count + :countDelta, sales_amount = sales_amount + :amountDelta, updated_at = NOW(6)",
            nativeQuery = true)
    void upsertSales(@Param("productId") Long productId, @Param("countDelta") long countDelta, @Param("amountDelta") BigDecimal amountDelta);
}
