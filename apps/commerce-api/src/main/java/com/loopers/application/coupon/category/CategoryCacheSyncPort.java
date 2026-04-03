package com.loopers.application.coupon.category;

import com.loopers.domain.category.Category;

import java.util.Collection;
import java.util.UUID;

public interface CategoryCacheSyncPort {

    void registerUpsert(Category category);

    void registerUpsertAll(Collection<Category> categories);

    void registerDelete(UUID categoryId);
}
