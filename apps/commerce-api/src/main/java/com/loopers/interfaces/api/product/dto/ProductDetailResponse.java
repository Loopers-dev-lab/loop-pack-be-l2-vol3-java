package com.loopers.interfaces.api.product.dto;

import com.loopers.application.product.ProductQueryUseCase;

public record ProductDetailResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        int price,
        Integer salePrice,
        boolean onSale,
        int stock,
        int likeCount,
        String description,
        Long rank
) {
    public static ProductDetailResponse from(ProductQueryUseCase.ProductDetailInfo info) {
        return new ProductDetailResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.price(),
                info.salePrice(),
                info.onSale(),
                info.stock(),
                info.likeCount(),
                info.description(),
                info.rank()
        );
    }
}
