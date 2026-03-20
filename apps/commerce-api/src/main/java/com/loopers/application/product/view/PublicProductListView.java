package com.loopers.application.product.view;

import java.util.List;

public record PublicProductListView(
        List<PublicProductListItemView> items,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages,
        boolean hasNext,
        String nextCursor
) {
}
