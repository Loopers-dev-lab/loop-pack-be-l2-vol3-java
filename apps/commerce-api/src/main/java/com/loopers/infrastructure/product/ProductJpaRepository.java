package com.loopers.infrastructure.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {
    List<ProductEntity> findAllByIdInAndDeletedAtIsNull(List<Long> ids);

    /** 원자적 좋아요 증가 (Lock 없이 동시성 제어) */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductEntity p SET p.likeCount = p.likeCount + 1, p.updatedAt = CURRENT_TIMESTAMP WHERE p.id = :id")
    int incrementLikeCount(@Param("id") Long id);

    /** 원자적 좋아요 감소 (Lock 없이 동시성 제어) */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductEntity p SET p.likeCount = p.likeCount - 1, p.updatedAt = CURRENT_TIMESTAMP WHERE p.id = :id AND p.likeCount > 0")
    int decrementLikeCount(@Param("id") Long id);
}
