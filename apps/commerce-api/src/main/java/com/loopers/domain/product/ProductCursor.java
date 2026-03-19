package com.loopers.domain.product;

import java.time.ZonedDateTime;

/**
 * 상품 목록 커서 데이터
 *
 * 정렬 타입에 따라 사용되는 필드가 다르다:
 * - LATEST: createdAt + id
 * - PRICE_ASC / PRICE_DESC: basePrice + id
 * - LIKES_DESC: likeCount + id
 */
public record ProductCursor(
        ProductSortType sort,
        ZonedDateTime createdAt,
        Integer basePrice,
        Integer likeCount,
        Long id
) {
    public static ProductCursor ofLatest(ZonedDateTime createdAt, Long id) {
        return new ProductCursor(ProductSortType.LATEST, createdAt, null, null, id);
    }

    public static ProductCursor ofPrice(ProductSortType sort, Integer basePrice, Long id) {
        return new ProductCursor(sort, null, basePrice, null, id);
    }

    public static ProductCursor ofLikes(Integer likeCount, Long id) {
        return new ProductCursor(ProductSortType.LIKES_DESC, null, null, likeCount, id);
    }
}
