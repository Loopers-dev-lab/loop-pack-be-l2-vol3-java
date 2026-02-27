package com.loopers.interfaces.api.brand.dto;

import com.loopers.application.brand.dto.FindBrandResDto;

import java.util.List;

public record FindBrandApiResDto(
        Long id,
        String name,
        String description,
        List<ProductDto> products
) {
    public record ProductDto(Long id, String name, int price, int stock, String displayStatus) {
        public static ProductDto from(FindBrandResDto.ProductDto dto) {
            return new ProductDto(dto.id(), dto.name(), dto.price(), dto.stock(), dto.displayStatus());
        }
    }

    public static FindBrandApiResDto from(FindBrandResDto dto) {
        return new FindBrandApiResDto(
                dto.id(),
                dto.name(),
                dto.description(),
                dto.products().stream().map(ProductDto::from).toList()
        );
    }
}
