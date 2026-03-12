package com.loopers.interfaces.brand.dto;

import com.loopers.application.brand.dto.BrandCriteria;
import com.loopers.application.brand.dto.BrandResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.ZonedDateTime;
import java.util.List;

public class AdminBrandV1Dto {

    public record RegisterRequest(
        @NotBlank(message = "브랜드명은 필수 입력값입니다.")
        @Size(max = 99, message = "브랜드명은 99자 이하여야 합니다.")
        String name
    ) {
        public BrandCriteria.Register toCriteria() {
            return new BrandCriteria.Register(name);
        }
    }

    public record UpdateRequest(
        @NotBlank(message = "브랜드명은 필수 입력값입니다.")
        @Size(max = 99, message = "브랜드명은 99자 이하여야 합니다.")
        String name
    ) {
        public BrandCriteria.Update toCriteria() {
            return new BrandCriteria.Update(name);
        }
    }

    public record DetailResponse(
        Long id,
        String name,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        ZonedDateTime deletedAt
    ) {
        public static DetailResponse from(BrandResult info) {
            return new DetailResponse(info.id(), info.name(), info.createdAt(), info.updatedAt(), info.deletedAt());
        }
    }

    public record ListResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<ListItem> items
    ) {
        public record ListItem(
            Long id,
            String name,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt,
            ZonedDateTime deletedAt
        ) {
            public static ListItem from(BrandResult info) {
                return new ListItem(info.id(), info.name(), info.createdAt(), info.updatedAt(), info.deletedAt());
            }
        }
    }
}
