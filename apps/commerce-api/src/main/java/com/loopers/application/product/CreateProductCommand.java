package com.loopers.application.product;

import java.util.Map;

public record CreateProductCommand(
    Map<Long, ProductItem> products
) {
    public record ProductItem(
        String name,
        Integer price,
        Integer stock
    ) {
    }
}
