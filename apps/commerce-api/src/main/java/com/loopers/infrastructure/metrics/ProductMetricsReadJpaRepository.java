package com.loopers.infrastructure.metrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProductMetricsReadJpaRepository extends JpaRepository<ProductMetricsReadEntity, Long> {

    @Transactional
    @Modifying
    @Query(value = "INSERT INTO product_metrics (product_id, like_count, view_count, sales_count, version, updated_at)"
            + " VALUES (:productId, :likeCount, 0, 0, 0, NOW())"
            + " ON DUPLICATE KEY UPDATE like_count = :likeCount, updated_at = NOW()",
            nativeQuery = true)
    void upsertLikeCount(@Param("productId") Long productId, @Param("likeCount") long likeCount);
}
