package com.loopers.interfaces.api.product.dto;

import com.loopers.application.product.dto.FindProductResDto;
import com.loopers.domain.product.vo.DisplayStatus;

public record FindProductApiResDto(
        Long id,
        String name,
        Long brandId,
        String brandName,
        int price,
        int stock,
        DisplayStatus displayStatus,
        long favoriteCnt,
        boolean isFavorite,
        Long ranking
) {
    public static FindProductApiResDto from(FindProductResDto dto) {
        return new FindProductApiResDto(
                dto.id(),
                dto.name(),
                dto.brandId(),
                dto.brandName(),
                dto.price(),
                dto.stock(),
                dto.displayStatus(),
                dto.favoriteCnt(),
                dto.isFavorite(),
                dto.ranking()
        );
    }
}
