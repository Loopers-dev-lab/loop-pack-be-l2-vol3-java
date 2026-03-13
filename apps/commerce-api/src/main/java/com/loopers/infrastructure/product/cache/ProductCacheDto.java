package com.loopers.infrastructure.product.cache;

public record ProductCacheDto(
        Long id,
        String name,
        Long brandId,
        String brandName,
        int price,
        int stock,
        String displayStatus
) {
}
