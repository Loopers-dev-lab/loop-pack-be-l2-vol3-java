package com.loopers.application.product;

import java.util.List;

/**
 * PLP 1페이지 캐시 저장용 DTO. Redis에 JSON으로 직렬화된다.
 */
public record ProductListCacheDto(
        long totalElements,
        int number,
        int size,
        List<ProductListItemInfo> content) {
}
