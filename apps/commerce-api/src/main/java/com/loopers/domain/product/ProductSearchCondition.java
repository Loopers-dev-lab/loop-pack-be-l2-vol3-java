package com.loopers.domain.product;

public record ProductSearchCondition(
    Long brandId,
    ProductSortType sortType,
    int page,
    int size
) {
    public static ProductSearchCondition of(Long brandId, ProductSortType sortType, int page, int size) {
        return new ProductSearchCondition(brandId, sortType, page, size);
    }

    public boolean hasBrandId() {
        return brandId != null;
    }
}
