package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;

import java.time.ZonedDateTime;

public record BrandResult(
    Long id,
    String name,
    String description,
    String logoUrl,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {
    public static BrandResult from(Brand brand) {
        return new BrandResult(
            brand.getId(),
            brand.getName(),
            brand.getDescription(),
            brand.getLogoUrl(),
            brand.getCreatedAt(),
            brand.getUpdatedAt()
        );
    }
}
