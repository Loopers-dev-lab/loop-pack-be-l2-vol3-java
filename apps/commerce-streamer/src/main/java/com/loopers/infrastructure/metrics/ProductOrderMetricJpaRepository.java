package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.MetricId;
import com.loopers.domain.metrics.ProductOrderMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductOrderMetricJpaRepository extends JpaRepository<ProductOrderMetric, MetricId> {

    @Query(value = """
            SELECT product_id, SUM(quantity) AS total
            FROM product_order_metrics
            WHERE bucket_time >= :from AND bucket_time < :to
            GROUP BY product_id
            ORDER BY total DESC
            LIMIT :lim
            """, nativeQuery = true)
    List<Object[]> sumQuantityByBucketTimeRange(@Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to,
                                                 @Param("lim") int lim);

    @Modifying
    @Query(value = """
            INSERT INTO product_order_metrics (product_id, bucket_time, order_count, quantity, sales_amount)
            VALUES (:productId, :bucketTime, :orderDelta, :qtyDelta, :amountDelta)
            ON DUPLICATE KEY UPDATE
                order_count  = order_count + :orderDelta,
                quantity     = quantity + :qtyDelta,
                sales_amount = sales_amount + :amountDelta
            """, nativeQuery = true)
    void upsert(@Param("productId") Long productId,
                @Param("bucketTime") LocalDateTime bucketTime,
                @Param("orderDelta") int orderDelta,
                @Param("qtyDelta") long qtyDelta,
                @Param("amountDelta") long amountDelta);
}
