package com.loopers.interfaces.api.brand.dto;

import com.loopers.application.brand.BrandQueryUseCase;

public record BrandResponse(
        Long id,
        String name,
        String description
) {
    public static BrandResponse from(BrandQueryUseCase.BrandInfo brandInfo) {
        return new BrandResponse(
                brandInfo.id(),
                brandInfo.name(),
                brandInfo.description()
        );
    }
}
