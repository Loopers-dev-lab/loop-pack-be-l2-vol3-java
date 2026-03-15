package com.loopers.infrastructure.product.cache;

import java.util.List;

public record ProductListCacheDto(
        List<ProductCacheDto> items,
        long totalElements
) {
}
