package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("ProductAppService 통합 테스트")
class ProductAppServiceIntegrationTest {

    @Autowired
    private ProductAppService productAppService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private BrandRepository brandRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Brand testBrand;

    @BeforeEach
    void setUp() {
        testBrand = brandRepository.save(Brand.create("테스트 브랜드"));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("브랜드별 상품 페이징 조회")
    class GetProductsByBrandIdTest {

        @Test
        @DisplayName("brandId로 필터링하고 likeCount DESC 정렬된 결과를 페이징 조회한다")
        void getProductsByBrandId_success() {
            // given
            Product p1 = productRepository.save(Product.create(testBrand.getId(), "상품A", Money.of(BigDecimal.valueOf(10000))));
            Product p2 = productRepository.save(Product.create(testBrand.getId(), "상품B", Money.of(BigDecimal.valueOf(20000))));
            Product p3 = productRepository.save(Product.create(testBrand.getId(), "상품C", Money.of(BigDecimal.valueOf(30000))));

            productAppService.increaseLikeCount(p2.getId());
            productAppService.increaseLikeCount(p2.getId());
            productAppService.increaseLikeCount(p2.getId());

            productAppService.increaseLikeCount(p3.getId());

            // when
            Page<Product> result = productAppService.getProductsByBrandId(testBrand.getId(), 0, 20);

            // then
            assertThat(result.getContent()).hasSize(3);
            assertThat(result.getContent().get(0).getName()).isEqualTo("상품B");
            assertThat(result.getContent().get(1).getName()).isEqualTo("상품C");
            assertThat(result.getContent().get(2).getName()).isEqualTo("상품A");
        }

        @Test
        @DisplayName("페이징이 정상 동작한다")
        void getProductsByBrandId_paging() {
            // given
            for (int i = 0; i < 25; i++) {
                productRepository.save(Product.create(testBrand.getId(), "상품" + i, Money.of(BigDecimal.valueOf(1000))));
            }

            // when
            Page<Product> firstPage = productAppService.getProductsByBrandId(testBrand.getId(), 0, 20);
            Page<Product> secondPage = productAppService.getProductsByBrandId(testBrand.getId(), 1, 20);

            // then
            assertThat(firstPage.getContent()).hasSize(20);
            assertThat(secondPage.getContent()).hasSize(5);
            assertThat(firstPage.getTotalElements()).isEqualTo(25);
            assertThat(firstPage.getTotalPages()).isEqualTo(2);
        }

        @Test
        @DisplayName("다른 브랜드 상품은 조회되지 않는다")
        void getProductsByBrandId_filterByBrand() {
            // given
            Brand otherBrand = brandRepository.save(Brand.create("다른 브랜드"));
            productRepository.save(Product.create(testBrand.getId(), "내 브랜드 상품", Money.of(BigDecimal.valueOf(10000))));
            productRepository.save(Product.create(otherBrand.getId(), "다른 브랜드 상품", Money.of(BigDecimal.valueOf(20000))));

            // when
            Page<Product> result = productAppService.getProductsByBrandId(testBrand.getId(), 0, 20);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("내 브랜드 상품");
        }

        @Test
        @DisplayName("삭제된 상품은 조회되지 않는다")
        void getProductsByBrandId_excludeDeleted() {
            // given
            Product active = productRepository.save(Product.create(testBrand.getId(), "활성 상품", Money.of(BigDecimal.valueOf(10000))));
            Product deleted = productRepository.save(Product.create(testBrand.getId(), "삭제 상품", Money.of(BigDecimal.valueOf(20000))));
            deleted.delete();
            productRepository.save(deleted);

            // when
            Page<Product> result = productAppService.getProductsByBrandId(testBrand.getId(), 0, 20);

            // then
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("활성 상품");
        }
    }
}
