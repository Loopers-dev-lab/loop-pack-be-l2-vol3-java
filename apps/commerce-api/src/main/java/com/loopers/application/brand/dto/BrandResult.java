package com.loopers.application.brand.dto;

import com.loopers.domain.brand.BrandModel;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;

public record BrandResult(Long id, String name, ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
    public static BrandResult from(BrandModel model) {
        return new BrandResult(model.getId(), model.getName(), model.getCreatedAt(), model.getUpdatedAt(), model.getDeletedAt());
    }

    public record ListPage(
            int page, int size, long totalElements, int totalPages,
            List<BrandResult> items
    ) {
        public static ListPage from(Page<BrandResult> resultPage) {
            return new ListPage(
                    resultPage.getNumber(),
                    resultPage.getSize(),
                    resultPage.getTotalElements(),
                    resultPage.getTotalPages(),
                    new ArrayList<>(resultPage.getContent()));
        }
    }
}
