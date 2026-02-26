package com.loopers.infrastructure.product;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortOrder;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.StockQuantity;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
            ProductModel product = ProductModel.create(brandId, "테스트 상품", Money.of(new BigDecimal("10000")),
                    StockQuantity.of(10));

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
            ProductModel product = ProductModel.create(brandId, "미삭제 상품", Money.of(new BigDecimal("5000")),
                    StockQuantity.of(5));
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
            ProductModel product = ProductModel.create(brandId, "삭제될 상품", Money.of(new BigDecimal("1000")),
                    StockQuantity.of(1));
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
            ProductModel product = ProductModel.create(brandId, "삭제된 상품", Money.of(new BigDecimal("2000")),
                    StockQuantity.of(2));
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

    @DisplayName("findNotDeleted 시")
    @Nested
    class FindNotDeleted {

        @Test
        @DisplayName("LATEST면 최신순(created_at DESC)으로 반환한다.")
        void findNotDeleted_withLatest_shouldOrderByCreatedAtDesc() {
            Long brandId = saveBrand("정렬브랜드");
            ProductModel p1 = productRepository
                    .save(ProductModel.create(brandId, "첫번째", Money.of(new BigDecimal("1000")), StockQuantity.of(1)));
            ProductModel p2 = productRepository
                    .save(ProductModel.create(brandId, "두번째", Money.of(new BigDecimal("2000")), StockQuantity.of(1)));
            ProductModel p3 = productRepository
                    .save(ProductModel.create(brandId, "세번째", Money.of(new BigDecimal("3000")), StockQuantity.of(1)));

            Page<ProductModel> page = productRepository.findNotDeleted(ProductSortOrder.LATEST, null,
                    PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(3);
            assertThat(page.getContent().get(0).getName()).isEqualTo("세번째");
            assertThat(page.getContent().get(1).getName()).isEqualTo("두번째");
            assertThat(page.getContent().get(2).getName()).isEqualTo("첫번째");
            assertThat(page.getTotalElements()).isEqualTo(3);
        }

        @Test
        @DisplayName("PRICE_ASC면 가격 오름차순으로 반환한다.")
        void findNotDeleted_withPriceAsc_shouldOrderByPriceAsc() {
            Long brandId = saveBrand("가격브랜드");
            productRepository
                    .save(ProductModel.create(brandId, "비쌈", Money.of(new BigDecimal("3000")), StockQuantity.of(1)));
            productRepository
                    .save(ProductModel.create(brandId, "쌈", Money.of(new BigDecimal("1000")), StockQuantity.of(1)));
            productRepository
                    .save(ProductModel.create(brandId, "중간", Money.of(new BigDecimal("2000")), StockQuantity.of(1)));

            Page<ProductModel> page = productRepository.findNotDeleted(ProductSortOrder.PRICE_ASC, null,
                    PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(3);
            assertThat(page.getContent().get(0).getPrice()).isEqualByComparingTo("1000");
            assertThat(page.getContent().get(1).getPrice()).isEqualByComparingTo("2000");
            assertThat(page.getContent().get(2).getPrice()).isEqualByComparingTo("3000");
        }

        @Test
        @DisplayName("PRICE_DESC면 가격 내림차순으로 반환한다.")
        void findNotDeleted_withPriceDesc_shouldOrderByPriceDesc() {
            Long brandId = saveBrand("가격브랜드2");
            productRepository
                    .save(ProductModel.create(brandId, "쌈", Money.of(new BigDecimal("1000")), StockQuantity.of(1)));
            productRepository
                    .save(ProductModel.create(brandId, "비쌈", Money.of(new BigDecimal("3000")), StockQuantity.of(1)));

            Page<ProductModel> page = productRepository.findNotDeleted(ProductSortOrder.PRICE_DESC, null,
                    PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(2);
            assertThat(page.getContent().get(0).getPrice()).isEqualByComparingTo("3000");
            assertThat(page.getContent().get(1).getPrice()).isEqualByComparingTo("1000");
        }

        @Test
        @DisplayName("brandId가 null이면 전체, 값이 있으면 해당 브랜드만 반환한다.")
        void findNotDeleted_withBrandId_shouldFilterByBrand() {
            Long brandA = saveBrand("브랜드A");
            Long brandB = saveBrand("브랜드B");
            productRepository
                    .save(ProductModel.create(brandA, "A상품", Money.of(new BigDecimal("1000")), StockQuantity.of(1)));
            productRepository
                    .save(ProductModel.create(brandB, "B상품", Money.of(new BigDecimal("2000")), StockQuantity.of(1)));

            Page<ProductModel> all = productRepository.findNotDeleted(ProductSortOrder.LATEST, null,
                    PageRequest.of(0, 10));
            Page<ProductModel> onlyA = productRepository.findNotDeleted(ProductSortOrder.LATEST, brandA,
                    PageRequest.of(0, 10));

            assertThat(all.getTotalElements()).isEqualTo(2);
            assertThat(onlyA.getTotalElements()).isEqualTo(1);
            assertThat(onlyA.getContent().get(0).getBrandId()).isEqualTo(brandA);
        }

        @Test
        @DisplayName("페이징 시 page·size·totalElements가 반영된다.")
        void findNotDeleted_withPaging_shouldReturnPage() {
            Long brandId = saveBrand("페이징브랜드");
            for (int i = 0; i < 5; i++) {
                productRepository.save(ProductModel.create(brandId, "상품" + i, Money.of(new BigDecimal(1000 + i)),
                        StockQuantity.of(1)));
            }

            Page<ProductModel> first = productRepository.findNotDeleted(ProductSortOrder.LATEST, null,
                    PageRequest.of(0, 2));
            Page<ProductModel> second = productRepository.findNotDeleted(ProductSortOrder.LATEST, null,
                    PageRequest.of(1, 2));

            assertThat(first.getContent()).hasSize(2);
            assertThat(first.getTotalElements()).isEqualTo(5);
            assertThat(first.getTotalPages()).isEqualTo(3);
            assertThat(second.getContent()).hasSize(2);
            assertThat(second.getNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("LIKES_DESC 호출 시 예외 없이 페이지를 반환한다.")
        void findNotDeleted_withLikesDesc_shouldReturnPage() {
            Long brandId = saveBrand("인기브랜드");
            productRepository
                    .save(ProductModel.create(brandId, "상품", Money.of(new BigDecimal("1000")), StockQuantity.of(1)));

            Page<ProductModel> page = productRepository.findNotDeleted(ProductSortOrder.LIKES_DESC, null,
                    PageRequest.of(0, 10));

            assertThat(page).isNotNull();
            assertThat(page.getContent()).isNotNull();
            assertThat(page.getTotalElements()).isEqualTo(1);
        }
    }
}
