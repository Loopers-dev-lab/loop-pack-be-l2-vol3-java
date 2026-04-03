package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {

    Optional<ProductMetrics> findByProductId(Long productId);

    // 행이 없으면 INSERT(초기값 1), 있으면 atomic +1
    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, order_count, view_count, updated_at) " +
            "VALUES (:productId, 1, 0, 0, NOW()) " +
            "ON DUPLICATE KEY UPDATE like_count = like_count + 1, updated_at = NOW()",
            nativeQuery = true)
    void incrementLikeCount(@Param("productId") Long productId);

    // 행이 없으면 INSERT(초기값 0), 있으면 atomic -1 (최소 0 보장)
    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, order_count, view_count, updated_at) " +
            "VALUES (:productId, 0, 0, 0, NOW()) " +
            "ON DUPLICATE KEY UPDATE like_count = GREATEST(like_count - 1, 0), updated_at = NOW()",
            nativeQuery = true)
    void decrementLikeCount(@Param("productId") Long productId);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, order_count, view_count, updated_at) " +
            "VALUES (:productId, 0, 0, 1, NOW()) " +
            "ON DUPLICATE KEY UPDATE order_count = order_count + 1, updated_at = NOW()",
            nativeQuery = true)
    void incrementOrderCount(@Param("productId") Long productId);

    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, order_count, view_count, updated_at) " +
            "VALUES (:productId, 0, 0, 0, NOW()) " +
            "ON DUPLICATE KEY UPDATE view_count = view_count + 1, updated_at = NOW()",
            nativeQuery = true)
    void incrementViewCount(@Param("productId") Long productId);
}
