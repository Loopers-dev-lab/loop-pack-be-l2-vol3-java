package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.util.List;

public class BrandV1Dto {

    public record CreateRequest(
        @NotBlank(message = "브랜드명은 필수입니다")
        @Size(max = 100, message = "브랜드명은 100자 이하여야 합니다")
        String name,

        @Size(max = 2000, message = "브랜드 설명은 2000자 이하여야 합니다")
        String description
    ) {
    }

    public record UpdateRequest(
        @NotBlank(message = "브랜드명은 필수입니다")
        @Size(max = 100, message = "브랜드명은 100자 이하여야 합니다")
        String name,

        @Size(max = 2000, message = "브랜드 설명은 2000자 이하여야 합니다")
        String description
    ) {
    }

    public record BrandResponse(
        Long id,
        String name,
        String description,
        boolean isDeleted
    ) {
        public static BrandResponse from(BrandDto.BrandInfo brandInfo) {
            return new BrandResponse(
                brandInfo.id(),
                brandInfo.name(),
                brandInfo.description(),
                brandInfo.isDeleted()
            );
        }
    }

    public record BrandPageResponse(
        List<BrandResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static BrandPageResponse from(Page<BrandDto.BrandInfo> page) {
            return new BrandPageResponse(
                page.getContent().stream().map(BrandResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
            );
        }
    }
}
