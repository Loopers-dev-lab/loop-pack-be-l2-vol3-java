package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;

public class BrandDto {

    public record BrandInfo(
        Long id,
        String name,
        String description,
        boolean isDeleted
    ) {
        public static BrandInfo from(Brand brand) {
            return new BrandInfo(
                brand.getId(),
                brand.getName(),
                brand.getDescription(),
                brand.isDeleted()
            );
        }
    }

    public record CreateCommand(
        String name,
        String description
    ) {
    }

    public record UpdateCommand(
        Long brandId,
        String name,
        String description
    ) {
    }
}
