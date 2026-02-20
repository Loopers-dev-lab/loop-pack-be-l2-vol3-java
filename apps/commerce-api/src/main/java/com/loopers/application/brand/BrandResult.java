package com.loopers.application.brand;

import java.time.ZonedDateTime;

import com.loopers.domain.brand.Brand;

public record BrandResult(
        Long id,
        String name,
        String logoUrl,
        String description,
        ZonedDateTime createdAt,
        ZonedDateTime deletedAt
) {

    public static BrandResult from(Brand brand) {
        return new BrandResult(
                brand.getId(),
                brand.getName(),
                brand.getLogoUrl(),
                brand.getDescription(),
                brand.getCreatedAt(),
                brand.getDeletedAt()
        );
    }
}
