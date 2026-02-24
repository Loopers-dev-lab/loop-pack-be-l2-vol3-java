package com.loopers.interfaces.api.brand;

import com.loopers.domain.brand.Brand;
import jakarta.validation.constraints.NotBlank;

public class BrandDto {

    public record CreateRequest(
        @NotBlank String name,
        String description
    ) {}

    public record UpdateRequest(
        @NotBlank String name,
        String description
    ) {}

    public record BrandResponse(
        Long id,
        String name,
        String description
    ) {
        public static BrandResponse from(Brand brand) {
            return new BrandResponse(brand.getId(), brand.getName(), brand.getDescription());
        }
    }
}
