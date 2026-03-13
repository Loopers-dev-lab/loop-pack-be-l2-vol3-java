package com.loopers.application.product.dto;

import com.loopers.domain.product.model.ProductItem;

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
    public static FindProductResDto from(ProductItem item) {
        return new FindProductResDto(
                item.id(),
                item.name(),
                item.brandId(),
                item.brandName(),
                item.price(),
                item.stock(),
                item.displayStatus(),
                item.favoriteCnt(),
                item.isFavorite()
        );
    }
}
