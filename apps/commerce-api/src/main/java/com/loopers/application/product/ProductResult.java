package com.loopers.application.product;

import com.loopers.domain.product.Product;

public record ProductResult(
        Long id,
        Long brandId,
        String name,
        String thumbnailUrl,
        Long price,
        Long stock,
        String description
) {

    public static ProductResult from(Product product) {
        return new ProductResult(
                product.getId(),
                product.getBrandId(),
                product.getName().getValue(),
                product.getThumbnailUrl().getValue(),
                product.getPrice().getAmount(),
                product.getStock().getValue(),
                product.getDescription()
        );
    }
}
