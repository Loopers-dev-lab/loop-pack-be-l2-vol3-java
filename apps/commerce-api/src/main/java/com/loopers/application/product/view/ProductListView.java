package com.loopers.application.product.view;

import java.util.List;

public record ProductListView(
        List<ProductView> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
