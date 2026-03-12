package com.loopers.domain.product;

/**
 * 상품 목록 정렬 조건.
 */
public enum ProductSort {
    /**
     * 최신순 (createdAt DESC)
     */
    LATEST,

    /**
     * 가격 낮은 순 (price ASC)
     */
    PRICE_ASC,

    /**
     * 좋아요 많은 순 (likeCount DESC)
     * Application Layer에서 Like BC 데이터와 조합하여 처리
     */
    LIKES_DESC
}