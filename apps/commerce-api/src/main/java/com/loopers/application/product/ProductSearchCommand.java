package com.loopers.application.product;

import com.loopers.domain.product.ProductSortType;

public record ProductSearchCommand(
    Long brandId,
    ProductSortType sortType,
    int page,
    int size
) {
    public boolean hasBrandId() {
        return brandId != null;
    }
}
