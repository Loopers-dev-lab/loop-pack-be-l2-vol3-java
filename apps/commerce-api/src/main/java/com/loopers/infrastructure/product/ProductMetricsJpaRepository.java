package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductMetricsModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsModel, Long> {

    Optional<ProductMetricsModel> findByRefProductId(Long refProductId);

    @Modifying(clearAutomatically = true)
    @Query(
            value = "INSERT INTO product_metrics (ref_product_id, like_count, created_at, updated_at) " +
                    "VALUES (:refProductId, 1, NOW(), NOW()) " +
                    "ON DUPLICATE KEY UPDATE like_count = like_count + 1, updated_at = NOW()",
            nativeQuery = true
    )
    void incrementLikeCount(@Param("refProductId") Long refProductId);

    @Modifying(clearAutomatically = true)
    @Query(
            value = "UPDATE product_metrics SET like_count = GREATEST(like_count - 1, 0), updated_at = NOW() " +
                    "WHERE ref_product_id = :refProductId",
            nativeQuery = true
    )
    void decrementLikeCount(@Param("refProductId") Long refProductId);
}
