package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

import java.time.ZonedDateTime;

public class ProductV1Dto {

    public record ProductResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        Long price,
        String description,
        int stockQuantity,
        String status,
        long likeCount,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.price(),
                info.description(),
                info.stockQuantity(),
                info.status(),
                info.likeCount(),
                info.createdAt(),
                info.updatedAt()
            );
        }
    }
}
