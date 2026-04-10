package com.loopers.interfaces.api.product.dto;

import com.loopers.application.product.dto.FindProductListResDto;
import com.loopers.domain.product.vo.DisplayStatus;

public record FindProductListApiResDto(
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
    public static FindProductListApiResDto from(FindProductListResDto dto) {
        return new FindProductListApiResDto(
                dto.id(),
                dto.name(),
                dto.brandId(),
                dto.brandName(),
                dto.price(),
                dto.stock(),
                dto.displayStatus(),
                dto.favoriteCnt(),
                dto.isFavorite()
        );
    }
}
