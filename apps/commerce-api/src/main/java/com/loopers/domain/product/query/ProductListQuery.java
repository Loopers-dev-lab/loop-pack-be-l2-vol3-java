package com.loopers.domain.product.query;
import java.util.UUID;

public record ProductListQuery(
        UUID brandId,
        UUID categoryId,
        Integer minPrice,
        Integer maxPrice,
        Boolean deleted,
        String sort,
        Integer page,
        Integer size
) {
}
