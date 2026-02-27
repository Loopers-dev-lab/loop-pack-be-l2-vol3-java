package com.loopers.application.brand.dto;

import com.loopers.domain.brand.model.Brand;

public record FindBrandListResDto(Long id, String name, String description) {

    public static FindBrandListResDto from(Brand brand) {
        return new FindBrandListResDto(
                brand.getId(),
                brand.getName().value(),
                brand.getDescription()
        );
    }
}
