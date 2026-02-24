package com.loopers.infrastructure.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class ProductRepositoryIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long saveBrand(String name) {
        BrandModel brand = BrandModel.create(name);
        return brandRepository.save(brand).getId();
    }

    @DisplayName("save 시")
    @Nested
    class Save {

        @DisplayName("저장한 상품을 findById로 조회할 수 있다.")
        @Test
        void save_shouldPersistAndFindById() {
            // given
            Long brandId = saveBrand("테스트 브랜드");
            ProductModel product = ProductModel.create(brandId, "테스트 상품", new BigDecimal("10000"), 10);

            // when
            ProductModel saved = productRepository.save(product);
            Optional<ProductModel> found = productRepository.findById(saved.getId());

            // then
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(saved.getId());
            assertThat(found.get().getBrandId()).isEqualTo(brandId);
            assertThat(found.get().getName()).isEqualTo("테스트 상품");
            assertThat(found.get().getPrice()).isEqualByComparingTo("10000");
            assertThat(found.get().getStockQuantity()).isEqualTo(10);
            assertThat(found.get().isDeleted()).isFalse();
        }

        @DisplayName("저장한 상품을 findByIdAndNotDeleted로 조회할 수 있다.")
        @Test
        void save_shouldBeFoundByFindByIdAndNotDeleted() {
            // given
            Long brandId = saveBrand("미삭제 브랜드");
            ProductModel product = ProductModel.create(brandId, "미삭제 상품", new BigDecimal("5000"), 5);
            ProductModel saved = productRepository.save(product);

            // when
            Optional<ProductModel> found = productRepository.findByIdAndNotDeleted(saved.getId());

            // then
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("미삭제 상품");
            assertThat(found.get().isDeleted()).isFalse();
        }
    }

    @DisplayName("findById 시")
    @Nested
    class FindById {

        @DisplayName("존재하지 않는 ID면 empty를 반환한다.")
        @Test
        void findById_withNonExistentId_shouldReturnEmpty() {
            // given
            Long nonExistentId = 999_999L;

            // when
            Optional<ProductModel> found = productRepository.findById(nonExistentId);

            // then
            assertThat(found).isEmpty();
        }
    }

    @DisplayName("findByIdAndNotDeleted 시")
    @Nested
    class FindByIdAndNotDeleted {

        @DisplayName("존재하지 않는 ID면 empty를 반환한다.")
        @Test
        void findByIdAndNotDeleted_withNonExistentId_shouldReturnEmpty() {
            // given
            Long nonExistentId = 999_999L;

            // when
            Optional<ProductModel> found = productRepository.findByIdAndNotDeleted(nonExistentId);

            // then
            assertThat(found).isEmpty();
        }

        @DisplayName("soft delete된 상품은 조회되지 않는다.")
        @Test
        void findByIdAndNotDeleted_whenProductIsDeleted_shouldReturnEmpty() {
            // given
            Long brandId = saveBrand("삭제될 브랜드");
            ProductModel product = ProductModel.create(brandId, "삭제될 상품", new BigDecimal("1000"), 1);
            ProductModel saved = productRepository.save(product);
            saved.delete();
            productRepository.save(saved);

            // when
            Optional<ProductModel> found = productRepository.findByIdAndNotDeleted(saved.getId());

            // then
            assertThat(found).isEmpty();
        }

        @DisplayName("soft delete된 상품은 findById로는 조회된다.")
        @Test
        void findById_whenProductIsDeleted_shouldStillReturnProduct() {
            // given
            Long brandId = saveBrand("삭제된 브랜드");
            ProductModel product = ProductModel.create(brandId, "삭제된 상품", new BigDecimal("2000"), 2);
            ProductModel saved = productRepository.save(product);
            saved.delete();
            productRepository.save(saved);

            // when
            Optional<ProductModel> found = productRepository.findById(saved.getId());

            // then
            assertThat(found).isPresent();
            assertThat(found.get().isDeleted()).isTrue();
        }
    }
}
