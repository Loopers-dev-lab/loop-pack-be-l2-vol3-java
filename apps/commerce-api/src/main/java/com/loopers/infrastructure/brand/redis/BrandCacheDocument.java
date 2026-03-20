package com.loopers.infrastructure.brand.redis;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.vo.BrandName;

import java.util.UUID;

public record BrandCacheDocument(
        UUID id,
        String name,
        String description,
        String imageUrl
) {
    public static BrandCacheDocument from(Brand brand) {
        return new BrandCacheDocument(
                brand.id(),
                brand.name().value(),
                brand.description(),
                brand.imageUrl()
        );
    }

    public Brand toDomain() {
        return new Brand(
                id,
                new BrandName(name),
                description,
                imageUrl
        );
    }
}
