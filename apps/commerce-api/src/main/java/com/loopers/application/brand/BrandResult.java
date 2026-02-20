package com.loopers.application.brand;

import com.loopers.domain.brand.Brand;

public record BrandResult(Long id, String name, String logoUrl, String description) {

    public static BrandResult from(Brand brand) {
        return new BrandResult(
                brand.getId(),
                brand.getName(),
                brand.getLogoUrl(),
                brand.getDescription()
        );
    }
}