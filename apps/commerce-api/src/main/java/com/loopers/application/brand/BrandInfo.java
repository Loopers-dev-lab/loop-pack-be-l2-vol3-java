package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;

import java.time.LocalDateTime;

public record BrandInfo(
        Long id,
        String name,
        String description,
        Status status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
    public enum Status {
        ACTIVE, DELETED
    }

    public static BrandInfo from(Brand brand) {
        return new BrandInfo(
                brand.getId(),
                brand.getName(),
                brand.getDescription(),
                brand.isDeleted() ? Status.DELETED : Status.ACTIVE,
                brand.getCreatedAt().toLocalDateTime(),
                brand.getUpdatedAt().toLocalDateTime(),
                brand.getDeletedAt() != null ? brand.getDeletedAt().toLocalDateTime() : null
        );
    }
}
