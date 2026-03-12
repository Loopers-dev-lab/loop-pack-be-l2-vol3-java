package com.loopers.interfaces.api.brand.admin;

import com.loopers.domain.brand.BrandModel;

import java.time.ZonedDateTime;

public class BrandAdminV1Dto {

    public record CreateRequest(
        String name,
        String description
    ) {}

    public record UpdateRequest(
        String name,
        String description
    ) {}

    public record BrandResponse(
        Long id,
        String name,
        String description,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        public static BrandResponse from(BrandModel model) {
            return new BrandResponse(
                model.getId(),
                model.name().value(),
                model.description(),
                model.getCreatedAt(),
                model.getUpdatedAt(),
                model.getDeletedAt()
            );
        }
    }
}
