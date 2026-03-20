package com.loopers.application.coupon.category;

import com.loopers.domain.category.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryCacheRepository {
    Optional<Category> findById(UUID id);

    List<Category> findAll();

    boolean existsById(UUID id);

    void save(Category category);

    void delete(UUID id);
}
