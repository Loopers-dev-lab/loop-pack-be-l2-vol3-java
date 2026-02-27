package com.loopers.interfaces.api.order.dto;

import com.loopers.application.order.dto.FindOrderProductResDto;

public record FindOrderProductApiResDto(
        Long productId,
        String productName,
        int price,
        int quantity
) {
    public static FindOrderProductApiResDto from(FindOrderProductResDto dto) {
        return new FindOrderProductApiResDto(
                dto.productId(),
                dto.productName(),
                dto.price(),
                dto.quantity()
        );
    }
}
