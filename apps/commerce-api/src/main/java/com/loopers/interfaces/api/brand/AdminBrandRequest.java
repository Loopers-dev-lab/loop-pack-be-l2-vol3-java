package com.loopers.interfaces.api.brand;

import com.loopers.domain.brand.BrandStatus;

/** 브랜드 어드민 API 요청 DTO */
public class AdminBrandRequest {

    public record CreateBrandRequest(
            String name,
            String description
    ) {}

    public record UpdateBrandRequest(
            String name,
            String description
    ) {}

    public record ChangeStatusRequest(
            BrandStatus status
    ) {}
}
