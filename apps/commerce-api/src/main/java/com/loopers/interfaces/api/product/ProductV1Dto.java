package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetail;

public class ProductV1Dto {

    public record ProductSummaryResponse(
        Long id, String name, int price, String brandName, String stockStatus
    ) {
        public static ProductSummaryResponse from(ProductDetail detail) {
            return new ProductSummaryResponse(
                detail.id(),
                detail.name(),
                detail.price(),
                detail.brandName(),
                detail.stockStatus() != null ? detail.stockStatus().name() : null
            );
        }
    }

    public record ProductDetailResponse(
        Long id, String name, String description, int price,
        Long brandId, String brandName, int likeCount, String stockStatus
    ) {
        public static ProductDetailResponse from(ProductDetail detail) {
            return new ProductDetailResponse(
                detail.id(),
                detail.name(),
                detail.description(),
                detail.price(),
                detail.brandId(),
                detail.brandName(),
                detail.likeCount(),
                detail.stockStatus() != null ? detail.stockStatus().name() : null
            );
        }
    }
}
