package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public record BrandRequest() {

    // Command

    public record Register(
            @NotBlank(message = "브랜드명은 필수입니다")
            @Size(max = 100, message = "브랜드명은 100자 이하여야 합니다")
            String name,

            @Size(max = 500, message = "브랜드 설명은 500자 이하여야 합니다")
            String description
    ) {
        public BrandCommand.Register toCommand() {
            return BrandCommand.Register.of(name, description);
        }
    }

    public record UpdateInfo(
            @Size(min = 1, max = 100, message = "브랜드명은 1~100자여야 합니다")
            String name,

            @Size(max = 500, message = "브랜드 설명은 500자 이하여야 합니다")
            String description
    ) {
        public BrandCommand.UpdateInfo toCommand() {
            return BrandCommand.UpdateInfo.of(name, description);
        }
    }

    // Query

    public record ListAll(
            String name,
            BrandStatus status,

            @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다")
            Integer page,

            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
            Integer size
    ) {
        public ListAll {
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

    public record ListActive(
            String name,

            @PositiveOrZero(message = "페이지 번호는 0 이상이어야 합니다")
            Integer page,

            @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
            @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
            Integer size
    ) {
        public ListActive {
            if (page == null) page = 0;
            if (size == null) size = 20;
        }

        public Pageable toPageable() {
            return PageRequest.of(page, size);
        }
    }
}
