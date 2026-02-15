package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import com.loopers.domain.PageResult;
import jakarta.validation.constraints.NotBlank;

import java.time.ZonedDateTime;
import java.util.List;

public class AdminBrandV1Dto {

    public record CreateRequest(
        @NotBlank(message = "브랜드 이름은 비어있을 수 없습니다.")
        String name
    ) {}

    public record UpdateRequest(
        @NotBlank(message = "브랜드 이름은 비어있을 수 없습니다.")
        String name
    ) {}

    public record BrandResponse(Long id, String name, ZonedDateTime createdAt, ZonedDateTime updatedAt) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(info.id(), info.name(), info.createdAt(), info.updatedAt());
        }
    }

    public record BrandPageResponse(
        List<BrandResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static BrandPageResponse from(PageResult<BrandInfo> result) {
            List<BrandResponse> content = result.items().stream()
                .map(BrandResponse::from)
                .toList();
            return new BrandPageResponse(content, result.page(), result.size(), result.totalElements(), result.totalPages());
        }
    }
}
