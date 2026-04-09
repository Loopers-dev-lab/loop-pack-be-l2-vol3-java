package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.MetricId;
import com.loopers.domain.metrics.ProductLikeMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ProductLikeMetricJpaRepository extends JpaRepository<ProductLikeMetric, MetricId> {

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
