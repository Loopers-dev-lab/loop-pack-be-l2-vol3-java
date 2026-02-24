package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public class BrandAdminV1Dto {

    // Command

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

    // Query

    public record ListRequest(
            String name,
            BrandStatus status,

            @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다")
            Integer page,

            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
            Integer size
    ) {
        public ListRequest {
            if (page == null) page = 0;
            if (size == null) size = 20;
        }

        public enum BrandStatus {
            ACTIVE, DELETED;

            public Boolean toDeleted() {
                return this == DELETED ? Boolean.TRUE : Boolean.FALSE;
            }
        }

        public Boolean toDeleted() {
            return status != null ? status.toDeleted() : null;
        }

        public Pageable toPageable() {
            return PageRequest.of(page, size);
        }
    }

    // Response

    public record BrandResponse(
            Long id,
            String name,
            String description,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime deletedAt
    ) {
        public static BrandResponse from(BrandInfo info) {
            return new BrandResponse(
                    info.id(),
                    info.name(),
                    info.description(),
                    info.status().name(),
                    info.createdAt(),
                    info.updatedAt(),
                    info.deletedAt()
            );
        }
    }
}
