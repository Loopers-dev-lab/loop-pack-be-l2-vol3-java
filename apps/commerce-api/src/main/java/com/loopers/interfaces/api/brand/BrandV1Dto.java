package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;

import java.time.ZonedDateTime;

public class BrandV1Dto {

    public record BrandResponse(
        Long id,
        String name,
        String description,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(
                info.id(),
                info.name(),
                info.description(),
                info.createdAt(),
                info.updatedAt()
            );
        }
    }
}
