package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, view_count, sales_count, updated_at) " +
            "VALUES (:productId, 0, 0, 0, '2000-01-01 00:00:00') " +
            "ON DUPLICATE KEY UPDATE product_id = product_id", nativeQuery = true)
    void insertIgnore(@Param("productId") Long productId);

    @Modifying
    @Query("UPDATE ProductMetrics m SET m.likeCount = m.likeCount + 1, m.updatedAt = :occurredAt " +
            "WHERE m.productId = :productId AND m.updatedAt <= :occurredAt")
    int incrementLikeCount(@Param("productId") Long productId, @Param("occurredAt") ZonedDateTime occurredAt);

    @Modifying
    @Query("UPDATE ProductMetrics m SET m.likeCount = CASE WHEN m.likeCount > 0 THEN m.likeCount - 1 ELSE 0 END, m.updatedAt = :occurredAt " +
            "WHERE m.productId = :productId AND m.updatedAt <= :occurredAt")
    int decrementLikeCount(@Param("productId") Long productId, @Param("occurredAt") ZonedDateTime occurredAt);

    @Modifying
    @Query("UPDATE ProductMetrics m SET m.viewCount = m.viewCount + 1, m.updatedAt = :occurredAt " +
            "WHERE m.productId = :productId AND m.updatedAt <= :occurredAt")
    int incrementViewCount(@Param("productId") Long productId, @Param("occurredAt") ZonedDateTime occurredAt);

    @Modifying
    @Query("UPDATE ProductMetrics m SET m.salesCount = m.salesCount + 1, m.updatedAt = :occurredAt " +
            "WHERE m.productId = :productId AND m.updatedAt <= :occurredAt")
    int incrementSalesCount(@Param("productId") Long productId, @Param("occurredAt") ZonedDateTime occurredAt);

    @Modifying
    @Query("UPDATE ProductMetrics m SET m.salesCount = CASE WHEN m.salesCount > 0 THEN m.salesCount - 1 ELSE 0 END, m.updatedAt = :occurredAt " +
            "WHERE m.productId = :productId AND m.updatedAt <= :occurredAt")
    int decrementSalesCount(@Param("productId") Long productId, @Param("occurredAt") ZonedDateTime occurredAt);
}
