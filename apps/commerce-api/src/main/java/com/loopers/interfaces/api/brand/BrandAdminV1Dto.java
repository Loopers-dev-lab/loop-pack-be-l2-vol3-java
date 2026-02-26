package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;

import java.time.LocalDateTime;

public class BrandAdminV1Dto {

    // Response

    public record BrandResponse(
            Long id,
            String name,
            String description,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt
    ) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(
                    info.id(),
                    info.name(),
                    info.description(),
                    info.status().name(),
                    info.createdAt(),
                    info.updatedAt(),
                    info.deletedAt()
            );
        }
    }
}
