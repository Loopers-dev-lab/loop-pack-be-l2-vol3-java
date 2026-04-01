package com.loopers.infrastructure.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * 상품 좋아요 수 동기화 전용 Repository (Streamer용)
 *
 * product_metrics와 products.like_count를 같은 TX에서 업데이트하여
 * 이중 파이프라인 문제를 해소한다.
 *
 * 원자적 UPDATE — 비관적 락 불필요, 증감 연산만 수행.
 */
public interface ProductLikeCountJpaRepository extends JpaRepository<ProductLikeCountEntity, Long> {

    @Modifying
    @Query("UPDATE ProductLikeCountEntity p SET p.likeCount = p.likeCount + 1 WHERE p.id = :productId")
    int incrementLikeCount(Long productId);

    @Modifying
    @Query("UPDATE ProductLikeCountEntity p SET p.likeCount = p.likeCount - 1 WHERE p.id = :productId AND p.likeCount > 0")
    int decrementLikeCount(Long productId);
}
