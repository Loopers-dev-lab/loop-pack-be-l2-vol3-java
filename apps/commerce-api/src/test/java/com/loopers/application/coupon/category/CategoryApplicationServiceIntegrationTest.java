package com.loopers.application.coupon.category;

import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.infrastructure.category.CategoryJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class CategoryApplicationServiceIntegrationTest {

    @Autowired
    private CategoryApplicationService categoryApplicationService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private CategoryJpaRepository categoryJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("CategoryApplicationService 통합: 목록 조회 가능")
    void list() {
        Category savedCategory = categoryRepository.save(new Category("카테고리통합"));
        Long persistedPk = categoryJpaRepository.findByReferenceIdAndDeletedAtIsNull(savedCategory.id())
                .orElseThrow()
                .getId();

        var page = categoryApplicationService.list(PageRequest.of(0, 20));

        assertThat(savedCategory.id()).isNotNull();
        assertThat(persistedPk).isNotNull().isPositive();
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).name()).isEqualTo("카테고리통합");
    }
}
