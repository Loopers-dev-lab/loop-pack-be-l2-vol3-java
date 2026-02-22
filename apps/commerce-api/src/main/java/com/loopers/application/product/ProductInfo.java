package com.loopers.application.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductStatus;

import java.time.ZonedDateTime;

public record ProductInfo(
        Long id,
        Long brandId,
        String name,
        String description,
        int basePrice,
        ProductStatus status,
        int likeCount,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static ProductInfo from(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("Product는 null일 수 없습니다.");
        }
        return new ProductInfo(
                product.getId(),
                product.getBrandId(),
                product.getName(),
                product.getDescription(),
                product.getBasePrice(),
                product.getStatus(),
                product.getLikeCount(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
