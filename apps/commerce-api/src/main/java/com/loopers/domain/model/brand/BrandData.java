package com.loopers.domain.model.brand;

import java.time.LocalDateTime;

public record BrandData(
        Long id,
        BrandName name,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}
