package com.loopers.infrastructure.product.cache;

import com.loopers.domain.product.vo.DisplayStatus;

public record ProductCacheDto(
        Long id,
        String name,
        Long brandId,
        String brandName,
        int price,
        int stock,
        DisplayStatus displayStatus
) {
}
