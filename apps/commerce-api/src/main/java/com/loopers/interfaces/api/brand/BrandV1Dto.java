package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;

import java.time.ZonedDateTime;

public class BrandV1Dto {

    public record RegisterRequest(
            String name,
            String description,
            String logoUrl
    ) {}

    public record UpdateRequest(
            String name,
            String description,
            String logoUrl
    ) {}

    public record Response(
            Long id,
            String name,
            String description,
            String logoUrl,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ) {
        public static Response from(BrandInfo brandInfo) {
            return new Response(
                    brandInfo.id(),
                    brandInfo.name(),
                    brandInfo.description(),
                    brandInfo.logoUrl(),
                    brandInfo.createdAt(),
                    brandInfo.updatedAt()
            );
        }
    }
}
