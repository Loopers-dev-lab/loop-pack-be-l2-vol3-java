package com.loopers.infrastructure.category;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

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

    @Override
    public void run(ApplicationArguments args) {
        if (categoryJpaRepository.count() > 0) {
            return;
        }

        List<CategoryEntity> seedEntities = DEFAULT_CATEGORIES.stream()
                .map(CategoryEntity::new)
                .toList();
        categoryJpaRepository.saveAll(seedEntities);
    }
}
