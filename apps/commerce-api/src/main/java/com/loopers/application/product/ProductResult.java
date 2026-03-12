package com.loopers.application.product;

import com.loopers.domain.product.Product;

import java.time.ZonedDateTime;

public record ProductResult(
    Long id,
    Long brandId,
    String name,
    String description,
    Long price,
    Integer stock,
    String imageUrl,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    public static ProductResult from(Product product) {
        return new ProductResult(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getPrice().amount(),
            product.getStock().quantity(),
            product.getImageUrl(),
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }
}
