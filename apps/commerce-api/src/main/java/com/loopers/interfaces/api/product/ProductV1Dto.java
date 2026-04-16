package com.loopers.interfaces.api.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductInfo;

public class ProductV1Dto {

    public record CreateRequest(
        Long brandId,
        String name,
        Integer price,
        Integer stock,
        String description,
        String imageUrl
    ) {}

    public record UpdateRequest(
        String name,
        Integer price,
        Integer stock,
        String description,
        String imageUrl
    ) {}

    public record ProductResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        Integer price,
        Integer stock,
        Integer likesCount,
        String description,
        String imageUrl
    ) {
        public static ProductResponse from(ProductInfo info) {
            return new ProductResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.price(),
                info.stock(),
                info.likesCount(),
                info.description(),
                info.imageUrl()
            );
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProductDetailResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        Integer price,
        Integer stock,
        Integer likesCount,
        String description,
        String imageUrl,
        Long rank
    ) {
        public static ProductDetailResponse from(ProductDetailInfo info) {
            return new ProductDetailResponse(
                info.id(),
                info.brandId(),
                info.brandName(),
                info.name(),
                info.price(),
                info.stock(),
                info.likesCount(),
                info.description(),
                info.imageUrl(),
                info.rank()
            );
        }
    }
}
