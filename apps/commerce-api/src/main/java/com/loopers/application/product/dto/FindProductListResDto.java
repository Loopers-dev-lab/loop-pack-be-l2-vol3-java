package com.loopers.application.product.dto;

import com.loopers.domain.product.model.ProductItem;
import com.loopers.domain.product.vo.DisplayStatus;

public record FindProductListResDto(
        Long id,
        String name,
        Long brandId,
        String brandName,
        int price,
        int stock,
        DisplayStatus displayStatus,
        long favoriteCnt,
        boolean isFavorite
) {
    public static FindProductListResDto from(ProductItem item) {
        return new FindProductListResDto(
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
