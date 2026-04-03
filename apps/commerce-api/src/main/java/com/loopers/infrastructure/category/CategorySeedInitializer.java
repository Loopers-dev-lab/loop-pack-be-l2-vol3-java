package com.loopers.infrastructure.category;

import com.loopers.domain.category.Category;
import com.loopers.application.coupon.category.CategoryCacheSyncPort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class CategorySeedInitializer implements ApplicationRunner {

    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "사료",
            "간식",
            "장난감",
            "위생",
            "산책"
    );

    private final CategoryJpaRepository categoryJpaRepository;
    private final CategoryCacheSyncPort categoryCacheSyncPort;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (categoryJpaRepository.count() > 0) {
            return;
        }

        List<CategoryEntity> seedEntities = DEFAULT_CATEGORIES.stream()
                .map(name -> new CategoryEntity(UUID.randomUUID(), name))
                .toList();
        List<Category> savedCategories = categoryJpaRepository.saveAll(seedEntities).stream()
                .map(CategoryEntity::toDomain)
                .toList();
        categoryCacheSyncPort.registerUpsertAll(savedCategories);
    }
}
