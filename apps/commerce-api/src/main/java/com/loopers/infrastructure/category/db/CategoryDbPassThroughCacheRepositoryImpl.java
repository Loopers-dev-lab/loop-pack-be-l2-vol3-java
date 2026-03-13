package com.loopers.infrastructure.category.db;

import com.loopers.application.coupon.category.CategoryCacheRepository;
import com.loopers.domain.category.Category;
import com.loopers.infrastructure.category.CategoryEntity;
import com.loopers.infrastructure.category.CategoryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "loopers.cache.brand-category", name = "enabled", havingValue = "false")
@RequiredArgsConstructor
public class CategoryDbPassThroughCacheRepositoryImpl implements CategoryCacheRepository {

    private final CategoryJpaRepository categoryJpaRepository;

    @Override
    public Optional<Category> findById(UUID id) {
        return categoryJpaRepository.findByReferenceIdAndDeletedAtIsNull(id)
                .map(CategoryEntity::toDomain);
    }

    @Override
    public List<Category> findAll() {
        return categoryJpaRepository.findAllByDeletedAtIsNullOrderByIdAsc().stream()
                .map(CategoryEntity::toDomain)
                .toList();
    }

    @Override
    public boolean existsById(UUID id) {
        return categoryJpaRepository.existsByReferenceIdAndDeletedAtIsNull(id);
    }

    @Override
    public void save(Category category) {
    }

    @Override
    public void delete(UUID id) {
    }
}
