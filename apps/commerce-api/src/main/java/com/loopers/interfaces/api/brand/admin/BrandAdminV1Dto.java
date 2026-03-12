package com.loopers.interfaces.api.brand.admin;

import com.loopers.application.brand.BrandInfo;

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
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(
                info.id(),
                info.name(),
                info.description(),
                info.createdAt(),
                info.updatedAt(),
                info.deletedAt()
            );
        }
    }
}
