package com.loopers.interfaces.api.product.dto;

import com.loopers.application.product.ProductQueryUseCase;

public record ProductSummaryResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        int price,
        Integer salePrice,
        boolean onSale,
        int likeCount
) {
    public static ProductSummaryResponse from(ProductQueryUseCase.ProductSummaryInfo info) {
        return new ProductSummaryResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.price(),
                info.salePrice(),
                info.onSale(),
                info.likeCount()
        );
    }
}
