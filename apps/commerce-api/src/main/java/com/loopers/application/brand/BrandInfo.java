package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandStatus;

import java.time.ZonedDateTime;

public record BrandInfo(
        Long id,
        String name,
        String description,
        BrandStatus status,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
    public static BrandInfo from(Brand brand) {
        if (brand == null) {
            throw new IllegalArgumentException("Brand는 null일 수 없습니다.");
        }
        return new BrandInfo(
                brand.getId(),
                brand.getName(),
                brand.getDescription(),
                brand.getStatus(),
                brand.getCreatedAt(),
                brand.getUpdatedAt()
        );
    }
}
