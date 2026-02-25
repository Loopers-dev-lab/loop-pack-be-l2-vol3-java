package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.common.PaginationQuery;
import org.springframework.data.domain.Pageable;

public record ProductListQuery(
        Long brandId,
        String sort,
        Integer page,
        Integer size
) {

    public ProductSortType resolvedSort() {
        return ProductSortType.from(sort);
    }

    public Pageable toPageable() {
        return new PaginationQuery(page, size).toPageable(resolvedSort().toSort());
    }
}
