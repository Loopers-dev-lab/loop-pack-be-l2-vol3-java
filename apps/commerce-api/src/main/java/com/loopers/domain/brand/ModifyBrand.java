package com.loopers.domain.brand;

public record ModifyBrand(
        Long brandId,
        String name,
        String logoUrl,
        String description
) {
}
