package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductInfo;

public class ProductAdminV1Dto {

    public record CreateRequest(String name, String description, int price, Long brandId, int stockQuantity) {}

    public record UpdateRequest(String name, String description, int price) {}

    public record UpdateStockRequest(int quantity) {}

    public record ProductAdminSummaryResponse(
        Long id, String name, int price, String brandName, int stockQuantity
    ) {
        public static ProductAdminSummaryResponse from(ProductInfo.AdminSummary info) {
            return new ProductAdminSummaryResponse(
                info.id(), info.name(), info.price(), info.brandName(), info.stockQuantity()
            );
        }
    }

    public record ProductAdminDetailResponse(
        Long id, String name, String description, int price,
        Long brandId, String brandName, int likeCount, int stockQuantity
    ) {
        public static ProductAdminDetailResponse from(ProductInfo.AdminDetail info) {
            return new ProductAdminDetailResponse(
                info.id(), info.name(), info.description(), info.price(),
                info.brandId(), info.brandName(), info.likeCount(), info.stockQuantity()
            );
        }
    }
}
