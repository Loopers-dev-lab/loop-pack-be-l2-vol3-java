package com.loopers.domain.product.query;
import java.util.UUID;

public record ProductListQuery(
        UUID brandId,
        String sort,
        Integer page,
        Integer size
) {
}
