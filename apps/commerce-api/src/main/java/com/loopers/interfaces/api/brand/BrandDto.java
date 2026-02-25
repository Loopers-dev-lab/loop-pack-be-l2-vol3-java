package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.command.CreateBrandCommand;
import com.loopers.application.brand.command.UpdateBrandCommand;
import com.loopers.domain.brand.Brand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

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
            return CreateBrandCommand.builder()
                    .name(name)
                    .description(description)
                    .imageUrl(imageUrl)
                    .build();
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
            return UpdateBrandCommand.builder()
                    .description(description)
                    .imageUrl(imageUrl)
                    .build();
        }
    }

    @Builder
    public record BrandResponse(
            Long id,
            String name,
            String description,
            String imageUrl
    ) {
        public static BrandResponse from(Brand brand) {
            return BrandResponse.builder()
                    .id(brand.id())
                    .name(brand.name().value())
                    .description(brand.description())
                    .imageUrl(brand.imageUrl())
                    .build();
        }
    }
}
