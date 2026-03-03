package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.command.CreateBrandCommand;
import com.loopers.application.brand.command.UpdateBrandCommand;
import com.loopers.domain.brand.Brand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public class BrandDto {

    @Builder
    public record CreateBrandRequest(
            @NotBlank(message = "브랜드 이름은 필수입니다")
            @Size(max = 50, message = "브랜드 이름은 50자를 초과할 수 없습니다")
            String name,
            String description,
            String imageUrl
    ) {
        @Override
        public String toString() {
            return "CreateBrandRequest[name=%s, description=%s, imageUrl=%s]"
                    .formatted(name, description, imageUrl);
        }

        public CreateBrandCommand toCommand() {
            return new CreateBrandCommand(name, description, imageUrl);
        }
    }

    @Builder
    public record UpdateBrandRequest(
            String description,
            String imageUrl
    ) {
        @Override
        public String toString() {
            return "UpdateBrandRequest[description=%s, imageUrl=%s]"
                    .formatted(description, imageUrl);
        }

        public UpdateBrandCommand toCommand() {
            return new UpdateBrandCommand(description, imageUrl);
        }
    }

    @Builder
    public record BrandResponse(
            UUID id,
            String name,
            String description,
            String imageUrl
    ) {
        public static BrandResponse from(Brand brand) {
            return new BrandResponse(brand.id(), brand.name().value(), brand.description(), brand.imageUrl());
        }
    }

    public record BrandListResponse(
            List<BrandResponse> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public static BrandListResponse from(Page<Brand> pageData) {
            List<BrandResponse> items = pageData.getContent().stream()
                    .map(BrandResponse::from)
                    .toList();

            return new BrandListResponse(
                    items,
                    pageData.getNumber(),
                    pageData.getSize(),
                    pageData.getTotalElements(),
                    pageData.getTotalPages()
            );
        }
    }
}
