package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandResult;
import com.loopers.domain.brand.BrandInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.ZonedDateTime;

public class BrandV1Dto {

    public record BrandResponse(
        Long id,
        String name,
        String description,
        String logoUrl,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
    ) {
        public static BrandResponse from(BrandResult result) {
            return new BrandResponse(
                result.id(),
                result.name(),
                result.description(),
                result.logoUrl(),
                result.createdAt(),
                result.updatedAt()
            );
        }
    }

    public record BrandCreateRequest(
        @NotBlank(message = "브랜드명은 필수입니다.")
        @Size(max = 100, message = "브랜드명은 100자를 초과할 수 없습니다.")
        String name,

        @Size(max = 500, message = "브랜드 설명은 500자를 초과할 수 없습니다.")
        String description,

        @Size(max = 500, message = "로고 URL은 500자를 초과할 수 없습니다.")
        String logoUrl
    ) {
        public BrandInfo toInfo() {
            return new BrandInfo(name, description, logoUrl);
        }
    }

    public record BrandUpdateRequest(
        @NotBlank(message = "브랜드명은 필수입니다.")
        @Size(max = 100, message = "브랜드명은 100자를 초과할 수 없습니다.")
        String name,

        @Size(max = 500, message = "브랜드 설명은 500자를 초과할 수 없습니다.")
        String description,

        @Size(max = 500, message = "로고 URL은 500자를 초과할 수 없습니다.")
        String logoUrl
    ) {
        public BrandInfo toInfo() {
            return new BrandInfo(name, description, logoUrl);
        }
    }
}
