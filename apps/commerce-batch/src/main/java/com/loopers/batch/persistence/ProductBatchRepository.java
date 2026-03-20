package com.loopers.batch.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 배치 전용 Product Repository.
 */
public interface ProductBatchRepository extends JpaRepository<ProductEntity, Long> {

    @Modifying
    @Query("UPDATE ProductEntity p SET p.likeCount = :likeCount WHERE p.id = :productId")
    int updateLikeCount(@Param("productId") Long productId, @Param("likeCount") Long likeCount);
}
