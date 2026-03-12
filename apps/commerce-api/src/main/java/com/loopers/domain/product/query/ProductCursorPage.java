package com.loopers.domain.product.query;

import com.loopers.domain.product.Product;

import java.util.List;

public record ProductCursorPage(
        List<Product> items,
        int size,
        boolean hasNext,
        String nextCursor
) {
}
