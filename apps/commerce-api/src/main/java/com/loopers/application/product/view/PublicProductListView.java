package com.loopers.application.product.view;

import java.util.List;

public record PublicProductListView(
        List<PublicProductListItemView> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
