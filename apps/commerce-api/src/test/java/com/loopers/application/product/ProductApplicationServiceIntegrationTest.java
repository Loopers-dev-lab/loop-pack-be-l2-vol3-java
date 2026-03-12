package com.loopers.application.product;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.brand.command.CreateBrandCommand;
import com.loopers.application.product.command.CreateProductCommand;
import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.domain.product.Product;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class ProductApplicationServiceIntegrationTest {

    @Autowired
    private ProductApplicationService productApplicationService;

    @Autowired
    private BrandApplicationService brandApplicationService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("ProductApplicationService 통합: 생성 후 조회 가능")
    void createAndGet() {
        UUID categoryId = categoryRepository.save(new Category("상품카테고리")).id();
        UUID brandId = brandApplicationService.create(new CreateBrandCommand("상품브랜드", "desc", "img")).id();

        Product created = productApplicationService.create(new CreateProductCommand(
                "상품통합",
                10000,
                10,
                "desc",
                categoryId,
                brandId
        ));

        Product found = productApplicationService.get(created.id());
        Long persistedPk = productJpaRepository.findByReferenceId(created.id())
                .orElseThrow()
                .getId();

        assertThat(found.id()).isEqualTo(created.id());
        assertThat(persistedPk).isNotNull().isPositive();
        assertThat(found.name()).isEqualTo("상품통합");
    }
}
