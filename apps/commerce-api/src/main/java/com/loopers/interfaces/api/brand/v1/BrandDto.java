package com.loopers.interfaces.api.brand.v1;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;

import com.loopers.application.brand.BrandResult;

public class BrandDto {

    public record CreateBrandRequest(
            @NotBlank(message = "브랜드명은 필수입니다.") String name,
            @NotBlank(message = "로고 URL은 필수입니다.") String logoUrl,
            String description
    ) {

    }

    public record CreateBrandResponse(Long brandId) {

        public static CreateBrandResponse from(BrandResult result) {
            return new CreateBrandResponse(result.id());
        }
    }

    public record BrandResponse(
            Long id,
            String name,
            String logoUrl,
            String description,
            ZonedDateTime createdAt,
            ZonedDateTime deletedAt
    ) {

        public static List<BrandResponse> from(List<BrandResult> results) {
            return results.stream()
                    .map(brand -> new BrandResponse(
                            brand.id(),
                            brand.name(),
                            brand.logoUrl(),
                            brand.description(),
                            brand.createdAt(),
                            brand.deletedAt()
                    ))
                    .toList();
        }
    }
}
