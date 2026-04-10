package com.loopers.domain.product;

/**
 * like_count 불일치 감지 결과.
 *
 * @param productId    상품 ID
 * @param currentCount 현재 products.like_count 값
 * @param actualCount  likes 테이블에서 집계한 실제 좋아요 수
 */
public record LikeCountMismatch(Long productId, long currentCount, long actualCount) {}
