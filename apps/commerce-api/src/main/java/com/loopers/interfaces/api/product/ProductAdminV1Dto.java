package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetail;

public class ProductAdminV1Dto {

    public record CreateRequest(String name, String description, int price, Long brandId, int stockQuantity) {}

    public record UpdateRequest(String name, String description, int price) {}

    public record UpdateStockRequest(int quantity) {}

    public record ProductAdminSummaryResponse(
        Long id, String name, int price, String brandName, int stockQuantity
    ) {
        public static ProductAdminSummaryResponse from(ProductDetail detail) {
            return new ProductAdminSummaryResponse(
                detail.id(), detail.name(), detail.price(), detail.brandName(), detail.stockQuantity()
            );
        }
    }

    public record ProductAdminDetailResponse(
        Long id, String name, String description, int price,
        Long brandId, String brandName, int likeCount, int stockQuantity
    ) {
        public static ProductAdminDetailResponse from(ProductDetail detail) {
            return new ProductAdminDetailResponse(
                detail.id(), detail.name(), detail.description(), detail.price(),
                detail.brandId(), detail.brandName(), detail.likeCount(), detail.stockQuantity()
            );
        }
    }
}
