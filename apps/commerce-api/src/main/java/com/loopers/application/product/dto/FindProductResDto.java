package com.loopers.application.product.dto;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.product.model.Product;

public record FindProductResDto(
        Long id,
        String name,
        Long brandId,
        String brandName,
        int price,
        int stock,
        String displayStatus,
        long favoriteCnt,
        boolean isFavorite
) {
    public static FindProductResDto of(Product product, Brand brand, long favoriteCnt, boolean isFavorite) {
        return new FindProductResDto(
                product.getId(),
                product.getName().value(),
                brand.getId(),
                brand.getName().value(),
                product.getPrice().value(),
                product.getStock().value(),
                product.getDisplayStatus().name(),
                favoriteCnt,
                isFavorite
        );
    }
}
