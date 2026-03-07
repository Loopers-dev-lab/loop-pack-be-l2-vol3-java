package com.loopers.infrastructure.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.ProductStatus;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
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

    private Brand createBrand(String name) {
        return brandRepository.save(Brand.register(name, name + " 설명"));
    }

    private Product createProduct(Long brandId, String name, int price) {
        return productRepository.save(Product.register(brandId, name, name + " 설명", price));
    }

    private Product createProduct(Long brandId, String name, int price, ProductStatus status) {
        Product product = Product.register(brandId, name, name + " 설명", price);
        product.changeStatus(status);
        return productRepository.save(product);
    }

    @Nested
    @DisplayName("save 메서드는")
    class Save {

        @Test
        void 새로운_상품을_저장하면_ID가_생성된다() {
            // arrange
            Brand brand = createBrand("나이키");

            // act
            Product saved = productRepository.save(
                    Product.register(brand.getId(), "에어맥스", "설명", 150000));

            // assert
            assertThat(saved.getId()).isNotNull();
        }
    }

    @Nested
    @DisplayName("findAll(page, size, brandId) 메서드는")
    class FindAllPaged {

        @Test
        void 삭제되지_않은_상품을_페이지네이션으로_반환한다() {
            // arrange
            Brand brand = createBrand("나이키");
            createProduct(brand.getId(), "상품1", 10000);
            createProduct(brand.getId(), "상품2", 20000);
            createProduct(brand.getId(), "상품3", 30000);

            // act
            List<Product> result = productRepository.findAll(0, 2, null);

            // assert
            assertThat(result).hasSize(2);
        }

        @Test
        void brandId로_필터링하여_반환한다() {
            // arrange
            Brand nike = createBrand("나이키");
            Brand adidas = createBrand("아디다스");
            createProduct(nike.getId(), "에어맥스", 150000);
            createProduct(nike.getId(), "에어포스", 120000);
            createProduct(adidas.getId(), "슈퍼스타", 100000);

            // act
            List<Product> result = productRepository.findAll(0, 20, nike.getId());

            // assert
            assertThat(result).hasSize(2);
        }

        @Test
        void 삭제된_상품은_제외된다() {
            // arrange
            Brand brand = createBrand("나이키");
            createProduct(brand.getId(), "상품1", 10000);
            Product deleted = createProduct(brand.getId(), "삭제상품", 20000);
            deleted.discontinue();
            productRepository.save(deleted);

            // act
            List<Product> result = productRepository.findAll(0, 20, null);

            // assert
            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("count(brandId) 메서드는")
    class Count {

        @Test
        void 삭제되지_않은_전체_상품_수를_반환한다() {
            // arrange
            Brand brand = createBrand("나이키");
            createProduct(brand.getId(), "상품1", 10000);
            createProduct(brand.getId(), "상품2", 20000);

            // act
            long result = productRepository.count(null);

            // assert
            assertThat(result).isEqualTo(2);
        }

        @Test
        void brandId로_필터링하여_수를_반환한다() {
            // arrange
            Brand nike = createBrand("나이키");
            Brand adidas = createBrand("아디다스");
            createProduct(nike.getId(), "상품1", 10000);
            createProduct(adidas.getId(), "상품2", 20000);

            // act
            long result = productRepository.count(nike.getId());

            // assert
            assertThat(result).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findAllDisplayable 메서드는")
    class FindAllDisplayable {

        @Test
        void ACTIVE와_SOLDOUT_상태만_반환한다() {
            // arrange
            Brand brand = createBrand("나이키");
            createProduct(brand.getId(), "활성상품", 10000, ProductStatus.ACTIVE);
            createProduct(brand.getId(), "품절상품", 20000, ProductStatus.SOLDOUT);
            createProduct(brand.getId(), "숨김상품", 30000, ProductStatus.HIDDEN);
            createProduct(brand.getId(), "단종상품", 40000, ProductStatus.DISCONTINUED);

            // act
            List<Product> result = productRepository.findAllDisplayable(null, ProductSortType.LATEST, 0, 20);

            // assert
            assertThat(result).hasSize(2);
        }

        @Test
        void LATEST_정렬은_최신순으로_반환한다() {
            // arrange
            Brand brand = createBrand("나이키");
            createProduct(brand.getId(), "상품1", 10000);
            createProduct(brand.getId(), "상품2", 20000);

            // act
            List<Product> result = productRepository.findAllDisplayable(null, ProductSortType.LATEST, 0, 20);

            // assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("상품2");
        }

        @Test
        void PRICE_ASC_정렬은_가격_오름차순으로_반환한다() {
            // arrange
            Brand brand = createBrand("나이키");
            createProduct(brand.getId(), "비싼상품", 300000);
            createProduct(brand.getId(), "싼상품", 100000);

            // act
            List<Product> result = productRepository.findAllDisplayable(null, ProductSortType.PRICE_ASC, 0, 20);

            // assert
            assertThat(result.get(0).getBasePrice()).isEqualTo(100000);
            assertThat(result.get(1).getBasePrice()).isEqualTo(300000);
        }

        @Test
        void brandId로_필터링하여_반환한다() {
            // arrange
            Brand nike = createBrand("나이키");
            Brand adidas = createBrand("아디다스");
            createProduct(nike.getId(), "에어맥스", 150000);
            createProduct(adidas.getId(), "슈퍼스타", 100000);

            // act
            List<Product> result = productRepository.findAllDisplayable(
                    nike.getId(), ProductSortType.LATEST, 0, 20);

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("에어맥스");
        }
    }

    @Nested
    @DisplayName("findAllActiveByBrandId 메서드는")
    class FindAllActiveByBrandId {

        @Test
        void 해당_브랜드의_ACTIVE_상품만_반환한다() {
            // arrange
            Brand brand = createBrand("나이키");
            createProduct(brand.getId(), "활성상품", 10000, ProductStatus.ACTIVE);
            createProduct(brand.getId(), "품절상품", 20000, ProductStatus.SOLDOUT);
            createProduct(brand.getId(), "숨김상품", 30000, ProductStatus.HIDDEN);

            // act
            List<Product> result = productRepository.findAllActiveByBrandId(brand.getId());

            // assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("활성상품");
        }
    }

    @Nested
    @DisplayName("findAllByBrandId 메서드는")
    class FindAllByBrandId {

        @Test
        void 해당_브랜드의_삭제되지_않은_전체_상품을_반환한다() {
            // arrange
            Brand brand = createBrand("나이키");
            createProduct(brand.getId(), "활성상품", 10000, ProductStatus.ACTIVE);
            createProduct(brand.getId(), "숨김상품", 20000, ProductStatus.HIDDEN);

            Product deleted = Product.register(brand.getId(), "삭제상품", "설명", 30000);
            deleted.discontinue();
            productRepository.save(deleted);

            // act
            List<Product> result = productRepository.findAllByBrandId(brand.getId());

            // assert
            assertThat(result).hasSize(2);
        }
    }
}
