package com.loopers.domain.product.query;

public record ProductListQuery(
        Long brandId,
        String sort,
        Integer page,
        Integer size
) {
}
