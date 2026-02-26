package com.loopers.interfaces.api.admin;

import jakarta.validation.constraints.NotBlank;

/**
 * 어드민 브랜드 API 전용 DTO. (interfaces 레이어, api-admin/v1)
 */
public final class AdminBrandV1Dto {

    public record CreateBrandRequest(
        @NotBlank(message = "브랜드 이름은 필수입니다.")
        String name
    ) {}

    public record UpdateBrandRequest(
        @NotBlank(message = "브랜드 이름은 필수입니다.")
        String name
    ) {}

    public record BrandResponse(Long id, String name) {}
}
