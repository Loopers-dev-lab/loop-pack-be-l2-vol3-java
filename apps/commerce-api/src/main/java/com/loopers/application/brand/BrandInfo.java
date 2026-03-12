package com.loopers.application.brand;

import com.loopers.domain.brand.BrandModel;

import java.time.ZonedDateTime;

public record BrandInfo(
    Long id,
    String name,
    String description,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt,
    ZonedDateTime deletedAt
) {

    public static BrandInfo from(BrandModel model) {
        return new BrandInfo(
            model.getId(),
            model.name().value(),
            model.description(),
            model.getCreatedAt(),
            model.getUpdatedAt(),
            model.getDeletedAt()
        );
    }
}
