package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO product_metrics (product_id, like_count, order_count)
            VALUES (:productId, :delta, 0)
            ON DUPLICATE KEY UPDATE like_count = like_count + :delta
            """, nativeQuery = true)
    void upsertLike(@Param("productId") Long productId, @Param("delta") int delta);

    @Modifying
    @Query(value = """
            INSERT INTO product_metrics (product_id, like_count, order_count)
            VALUES (:productId, 0, :quantity)
            ON DUPLICATE KEY UPDATE order_count = order_count + :quantity
            """, nativeQuery = true)
    void upsertOrder(@Param("productId") Long productId, @Param("quantity") long quantity);
}
