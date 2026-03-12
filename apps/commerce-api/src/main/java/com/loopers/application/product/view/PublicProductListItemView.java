package com.loopers.application.product.view;

import com.loopers.domain.product.Product;

import java.util.UUID;

public record PublicProductListItemView(
        UUID id,
        String name,
        Integer price,
        Integer stock,
        UUID categoryId,
        UUID brandId,
        String brandName,
        Integer likeCount
) {
    public static PublicProductListItemView from(Product product, String brandName) {
        return new PublicProductListItemView(
                product.id(),
                product.name(),
                product.price(),
                product.stock(),
                product.categoryId(),
                product.brandId(),
                brandName,
                product.likeCount()
        );
    }
}
