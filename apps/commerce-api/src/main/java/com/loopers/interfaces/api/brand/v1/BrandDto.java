package com.loopers.interfaces.api.brand.v1;

import com.loopers.application.brand.BrandResult;

import jakarta.validation.constraints.NotBlank;

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
}
