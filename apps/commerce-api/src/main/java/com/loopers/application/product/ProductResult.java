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
    Long likeCount,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    public static ProductResult from(Product product, Long likeCount) {
        return new ProductResult(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getDescription(),
            product.getPrice().amount(),
            product.getStock().quantity(),
            product.getImageUrl(),
            likeCount != null ? likeCount : 0L,
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }

    public static ProductResult from(Product product) {
        return from(product, 0L);
    }
}
