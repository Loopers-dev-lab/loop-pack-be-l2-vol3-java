package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductListInfo;

public class ProductV1Dto {

    public record ProductDetailResponse(
        Long id,
        String name,
        Long price,
        int stockQuantity,
        BrandResponse brand,
        long likeCount
    ) {
        public static ProductDetailResponse from(ProductDetailInfo info) {
            BrandResponse brandResponse = info.brand() != null
                ? new BrandResponse(info.brand().id(), info.brand().name())
                : null;
            return new ProductDetailResponse(
                info.id(),
                info.name(),
                info.price(),
                info.stockQuantity(),
                brandResponse,
                info.likeCount()
            );
        }
    }

    public record BrandResponse(Long id, String name) {}

    public record ProductListResponse(
        Long id,
        String name,
        Long price,
        String brandName,
        long likeCount
    ) {
        public static ProductListResponse from(ProductListInfo info) {
            return new ProductListResponse(
                info.id(),
                info.name(),
                info.price(),
                info.brandName(),
                info.likeCount()
            );
        }
    }
}
