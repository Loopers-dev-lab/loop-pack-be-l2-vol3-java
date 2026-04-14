package com.loopers.infrastructure;

import com.loopers.domain.ProductMetrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductMetricsRepository extends JpaRepository<ProductMetrics, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO product_metrics (product_id, like_count, view_count, order_line_count)
            VALUES (:productId, 1, 0, 0)
            ON DUPLICATE KEY UPDATE like_count = like_count + 1
            """, nativeQuery = true)
    void incrementLikeCount(@Param("productId") Long productId);

    @Modifying
    @Query(value = """
            INSERT INTO product_metrics (product_id, like_count, view_count, order_line_count)
            VALUES (:productId, 0, 0, 0)
            ON DUPLICATE KEY UPDATE like_count = GREATEST(like_count - 1, 0)
            """, nativeQuery = true)
    void decrementLikeCount(@Param("productId") Long productId);

    @Modifying
    @Query(value = """
            INSERT INTO product_metrics (product_id, like_count, view_count, order_line_count)
            VALUES (:productId, 0, 1, 0)
            ON DUPLICATE KEY UPDATE view_count = view_count + 1
            """, nativeQuery = true)
    void incrementViewCount(@Param("productId") Long productId);

    @Modifying
    @Query(value = """
            INSERT INTO product_metrics (product_id, like_count, view_count, order_line_count)
            VALUES (:productId, 0, 0, 1)
            ON DUPLICATE KEY UPDATE order_line_count = order_line_count + 1
            """, nativeQuery = true)
    void incrementOrderLineCount(@Param("productId") Long productId);
}
