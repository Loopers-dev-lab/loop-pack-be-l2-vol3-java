package com.loopers.interfaces.api.product.admin;

import com.loopers.application.product.ProductDetail;

import java.time.ZonedDateTime;

public class ProductAdminV1Dto {

    public record CreateRequest(
        String name,
        String description,
        int price,
        Long brandId,
        int initialStock
    ) {}

    public record UpdateRequest(
        String name,
        String description,
        int price
    ) {}

    public record ProductResponse(
        Long id,
        String name,
        String description,
        int price,
        Long brandId,
        String brandName,
        int likeCount,
        int stockQuantity,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        public static ProductResponse from(ProductDetail detail) {
            return new ProductResponse(
                detail.id(),
                detail.name(),
                detail.description(),
                detail.price(),
                detail.brandId(),
                detail.brandName(),
                detail.likeCount(),
                detail.stockQuantity(),
                detail.createdAt(),
                detail.updatedAt(),
                detail.deletedAt()
            );
        }
    }
}
