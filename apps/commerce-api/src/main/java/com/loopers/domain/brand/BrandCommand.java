package com.loopers.domain.brand;

public record BrandCommand(
        String name,
        String description,
        String logoUrl
) {}
