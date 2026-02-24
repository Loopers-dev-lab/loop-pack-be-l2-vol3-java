package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public class BrandAdminV1Dto {

    public record RegisterRequest(
            @NotBlank(message = "브랜드명은 필수입니다")
            @Size(max = 100, message = "브랜드명은 100자 이하여야 합니다")
            String name,

            @Size(max = 500, message = "브랜드 설명은 500자 이하여야 합니다")
            String description
    ) {}

    public record UpdateRequest(
            @Size(min = 1, max = 100, message = "브랜드명은 1~100자여야 합니다")
            String name,

            @Size(max = 500, message = "브랜드 설명은 500자 이하여야 합니다")
            String description
    ) {}

    public record BrandResponse(
            Long id,
            String name,
            String description,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(
                    info.id(),
                    info.name(),
                    info.description(),
                    info.status().name(),
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }
}
