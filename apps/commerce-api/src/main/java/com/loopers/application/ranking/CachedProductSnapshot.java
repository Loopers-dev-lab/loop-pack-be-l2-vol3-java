package com.loopers.application.ranking;

import com.loopers.domain.product.ProductModel;

import java.math.BigDecimal;

public record CachedProductSnapshot(
        Long id,
        String productId,
        String productName,
        BigDecimal price,
        boolean deleted
) {
    public static CachedProductSnapshot from(ProductModel product) {
        return new CachedProductSnapshot(
                product.getId(),
                product.getProductId().value(),
                product.getProductName().value(),
                product.getPrice().value(),
                product.isDeleted()
        );
    }
}
