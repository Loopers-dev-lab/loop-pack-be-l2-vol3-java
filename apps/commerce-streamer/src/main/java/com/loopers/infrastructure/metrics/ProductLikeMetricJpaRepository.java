package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.MetricId;
import com.loopers.domain.metrics.ProductLikeMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductLikeMetricJpaRepository extends JpaRepository<ProductLikeMetric, MetricId> {

    @Query(value = """
            SELECT product_id, SUM(like_count) AS total
            FROM product_like_metrics
            WHERE bucket_time >= :from AND bucket_time < :to
            GROUP BY product_id
            ORDER BY total DESC
            LIMIT :lim
            """, nativeQuery = true)
    List<Object[]> sumByBucketTimeRange(@Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to,
                                        @Param("lim") int lim);

    @Modifying
    @Query(value = """
            INSERT INTO product_like_metrics (product_id, bucket_time, like_count)
            VALUES (:productId, :bucketTime, :delta)
            ON DUPLICATE KEY UPDATE like_count = like_count + :delta
            """, nativeQuery = true)
    void upsert(@Param("productId") Long productId,
                @Param("bucketTime") LocalDateTime bucketTime,
                @Param("delta") int delta);
}
