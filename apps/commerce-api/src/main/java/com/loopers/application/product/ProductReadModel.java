package com.loopers.application.product;

import com.loopers.domain.product.Product;

import java.time.ZonedDateTime;

public record ProductReadModel(
    Long id,
    Long brandId,
    String name,
    int price,
    int likeCount,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    public static ProductReadModel from(Product product) {
        return new ProductReadModel(
            product.getId(),
            product.getBrandId(),
            product.getName(),
            product.getPrice().amount(),
            product.getLikeCount(),
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }
}
