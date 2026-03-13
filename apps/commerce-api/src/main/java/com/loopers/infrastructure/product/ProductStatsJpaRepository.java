package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductStatsModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductStatsJpaRepository extends JpaRepository<ProductStatsModel, Long> {

    @Modifying
    @Query(value = "INSERT IGNORE INTO product_stats (product_id, like_count) VALUES (:productId, 0)", nativeQuery = true)
    void insertIgnore(@Param("productId") Long productId);

    @Modifying
    @Query(value = "UPDATE product_stats SET like_count = like_count + 1 WHERE product_id = :productId", nativeQuery = true)
    void incrementLikeCount(@Param("productId") Long productId);

    @Modifying
    @Query(value = "UPDATE product_stats SET like_count = GREATEST(0, like_count - 1) WHERE product_id = :productId", nativeQuery = true)
    void decrementLikeCount(@Param("productId") Long productId);
}
