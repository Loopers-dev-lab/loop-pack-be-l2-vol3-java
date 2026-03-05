package com.loopers.domain.product;

public record ProductSpec(
        Long brandId,
        String name,
        String thumbnailUrl,
        Long price,
        Long stock,
        String description
) {
}
