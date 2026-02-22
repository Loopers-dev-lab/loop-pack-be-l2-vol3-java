package com.loopers.application.product;

import java.time.ZonedDateTime;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;

public record ProductDetail(
        Long productId,
        String name,
        String thumbnailUrl,
        Long price,
        Long stock,
        String description,
        ZonedDateTime createdAt,
        Long brandId,
        String brandName,
        String brandLogoUrl,
        Long likeCount,
        boolean liked
) {

    public static ProductDetail from(Product product, Brand brand, Long likeCount, boolean liked) {
        return new ProductDetail(
                product.getId(),
                product.getName().getValue(),
                product.getThumbnailUrl().getValue(),
                product.getPrice().getAmount(),
                product.getStock().getValue(),
                product.getDescription(),
                product.getCreatedAt(),
                brand.getId(),
                brand.getName(),
                brand.getLogoUrl(),
                likeCount,
                liked
        );
    }
}
