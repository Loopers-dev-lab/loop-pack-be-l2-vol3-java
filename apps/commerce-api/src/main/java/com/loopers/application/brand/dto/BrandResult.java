package com.loopers.application.brand.dto;

import com.loopers.domain.brand.BrandModel;
import java.time.ZonedDateTime;

public record BrandResult(Long id, String name, ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
    public static BrandResult from(BrandModel model) {
        return new BrandResult(model.getId(), model.getName(), model.getCreatedAt(), model.getUpdatedAt(), model.getDeletedAt());
    }
}
