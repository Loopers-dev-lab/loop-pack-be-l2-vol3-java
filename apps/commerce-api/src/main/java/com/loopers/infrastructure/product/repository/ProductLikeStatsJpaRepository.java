package com.loopers.infrastructure.product.repository;

import com.loopers.infrastructure.product.entity.ProductLikeStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductLikeStatsJpaRepository extends JpaRepository<ProductLikeStatsEntity, Long> {

    @Modifying
    @Query("UPDATE ProductLikeStatsEntity s SET s.likeCount = s.likeCount + 1 WHERE s.productId = :productId")
    int increaseLikeCount(@Param("productId") Long productId);

    @Modifying
    @Query("UPDATE ProductLikeStatsEntity s SET s.likeCount = s.likeCount - 1 WHERE s.productId = :productId AND s.likeCount > 0")
    int decreaseLikeCount(@Param("productId") Long productId);
}
