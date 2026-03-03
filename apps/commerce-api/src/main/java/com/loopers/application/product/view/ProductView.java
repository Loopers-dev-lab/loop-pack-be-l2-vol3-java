package com.loopers.application.product.view;

import com.loopers.domain.product.Product;

import java.time.ZonedDateTime;
import java.util.UUID;

public record ProductView(
        UUID id,
        String name,
        Integer price,
        Integer stock,
        String description,
        UUID categoryId,
        UUID brandId,
        String brandName,
        Integer likeCount,
        ZonedDateTime deletedAt
) {
    public static ProductView from(Product product, String brandName) {
        return new ProductView(
                product.id(),
                product.name(),
                product.price(),
                product.stock(),
                product.description(),
                product.categoryId(),
                product.brandId(),
                brandName,
                product.likeCount(),
                product.deletedAt()
        );
    }
}
