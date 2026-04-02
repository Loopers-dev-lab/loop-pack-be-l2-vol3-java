package com.loopers.infrastructure.metrics;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsEntity, Long> {

    Optional<ProductMetricsEntity> findByProductId(Long productId);

    @Modifying
    @Query("UPDATE ProductMetricsEntity m SET m.likeCount = m.likeCount + 1 WHERE m.productId = :productId")
    void incrementLikeCount(@Param("productId") Long productId);

    @Modifying
    @Query("UPDATE ProductMetricsEntity m SET m.likeCount = m.likeCount - 1 WHERE m.productId = :productId AND m.likeCount > 0")
    void decrementLikeCount(@Param("productId") Long productId);

    @Modifying
    @Query("UPDATE ProductMetricsEntity m SET m.viewCount = m.viewCount + 1 WHERE m.productId = :productId")
    void incrementViewCount(@Param("productId") Long productId);

    @Modifying
    @Query("UPDATE ProductMetricsEntity m SET m.salesCount = m.salesCount + 1 WHERE m.productId = :productId")
    void incrementSalesCount(@Param("productId") Long productId);
}
