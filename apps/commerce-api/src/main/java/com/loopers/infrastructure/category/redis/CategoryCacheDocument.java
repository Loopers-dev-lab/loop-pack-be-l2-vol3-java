package com.loopers.infrastructure.category.redis;

import com.loopers.domain.category.Category;

import java.util.UUID;

public record CategoryCacheDocument(
        UUID id,
        String name
) {
    public static CategoryCacheDocument from(Category category) {
        return new CategoryCacheDocument(category.id(), category.name());
    }

    public Category toDomain() {
        return new Category(id, name);
    }
}
