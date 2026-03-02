package com.loopers.interfaces.api.order.dto;

import com.loopers.application.order.dto.FindOrderResDto;

import java.util.List;

public record FindOrderApiResDto(
        Long id,
        int totalPrice,
        List<FindOrderProductApiResDto> orderProducts
) {
    public static FindOrderApiResDto from(FindOrderResDto dto) {
        List<FindOrderProductApiResDto> products = dto.orderProducts().stream()
                .map(FindOrderProductApiResDto::from)
                .toList();
        return new FindOrderApiResDto(dto.id(), dto.totalPrice(), products);
    }
}
