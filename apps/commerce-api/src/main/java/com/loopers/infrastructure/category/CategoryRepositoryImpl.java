package com.loopers.infrastructure.category;

import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CategoryRepositoryImpl implements CategoryRepository {
    private final CategoryJpaRepository categoryJpaRepository;

    @Override
    public Category save(Category category) {
        CategoryEntity saved = categoryJpaRepository.save(CategoryEntity.from(category));
        return saved.toDomain();
    }

    @Override
    public Optional<Category> findById(Long id) {
        return categoryJpaRepository.findByIdAndDeletedAtIsNull(id)
                .map(CategoryEntity::toDomain);
    }

    @Override
    public Page<Category> findAll(Pageable pageable) {
        return categoryJpaRepository.findAllByDeletedAtIsNull(pageable)
                .map(CategoryEntity::toDomain);
    }
}
