package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;

import java.time.LocalDateTime;

public class BrandV1Dto {

    // Response

    public record BrandResponse(
            Long id,
            String name,
            String description,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
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
