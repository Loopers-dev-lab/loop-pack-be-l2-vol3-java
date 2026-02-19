package com.loopers.interfaces.api.brand.dto;

import com.loopers.domain.brand.Brand;
import jakarta.validation.constraints.NotBlank;

import java.time.ZonedDateTime;

public class BrandV1Dto {

    public record CreateRequest(
            @NotBlank(message = "브랜드 이름은 필수값입니다.")
            String name,
            String description
    ) {}

    public record UpdateRequest(
            @NotBlank(message = "브랜드 이름은 필수값입니다.")
            String name,
            String description
    ) {}

    public record BrandResponse(Long id, String name, String description) {
        public static BrandResponse from(Brand brand) {
            return new BrandResponse(brand.getId(), brand.getName(), brand.getDescription());
        }
    }

    public record AdminBrandResponse(Long id, String name, String description,
                                     ZonedDateTime createdAt, ZonedDateTime updatedAt) {
        public static AdminBrandResponse from(Brand brand) {
            return new AdminBrandResponse(
                    brand.getId(), brand.getName(), brand.getDescription(),
                    brand.getCreatedAt(), brand.getUpdatedAt()
            );
        }
    }
}