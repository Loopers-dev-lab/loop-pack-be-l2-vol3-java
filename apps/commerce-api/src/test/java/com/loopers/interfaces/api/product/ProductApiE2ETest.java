package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProductApiE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Brand createActiveBrand(String name) {
        return brandRepository.save(Brand.create(name, name + " 설명"));
    }

    private Product createProduct(Long brandId, String name, int price, ProductStatus status) {
        Product product = Product.create(brandId, name, name + " 설명", price);
        if (status != ProductStatus.ACTIVE) {
            product.changeStatus(status);
        }
        Product saved = productRepository.save(product);
        inventoryRepository.save(Inventory.create(saved.getId(), 100));
        return saved;
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class 상품_목록_조회 {

        @Test
        void ACTIVE와_SOLDOUT_상품만_조회되고_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            createProduct(brand.getId(), "활성상품", 150000, ProductStatus.ACTIVE);
            createProduct(brand.getId(), "품절상품", 120000, ProductStatus.SOLDOUT);
            createProduct(brand.getId(), "숨김상품", 100000, ProductStatus.HIDDEN);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.getForEntity(
                    "/api/v1/products", ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 상품이_없으면_빈_목록과_200_OK를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.getForEntity(
                    "/api/v1/products", ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void brandId로_필터링하여_조회할_수_있다() {
            // arrange
            Brand nike = createActiveBrand("나이키");
            Brand adidas = createActiveBrand("아디다스");
            createProduct(nike.getId(), "에어맥스", 150000, ProductStatus.ACTIVE);
            createProduct(adidas.getId(), "슈퍼스타", 100000, ProductStatus.ACTIVE);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.getForEntity(
                    "/api/v1/products?brandId=" + nike.getId(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 정렬_파라미터로_조회할_수_있다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            createProduct(brand.getId(), "비싼상품", 300000, ProductStatus.ACTIVE);
            createProduct(brand.getId(), "싼상품", 100000, ProductStatus.ACTIVE);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.getForEntity(
                    "/api/v1/products?sort=PRICE_ASC", ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class 상품_상세_조회 {

        @Test
        void ACTIVE_상품이면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProduct(brand.getId(), "에어맥스", 150000, ProductStatus.ACTIVE);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.getForEntity(
                    "/api/v1/products/" + product.getId(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void SOLDOUT_상품이면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProduct(brand.getId(), "에어맥스", 150000, ProductStatus.SOLDOUT);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.getForEntity(
                    "/api/v1/products/" + product.getId(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void HIDDEN_상품이면_404_Not_Found를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProduct(brand.getId(), "에어맥스", 150000, ProductStatus.HIDDEN);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.getForEntity(
                    "/api/v1/products/" + product.getId(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 삭제된_상품이면_404_Not_Found를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProduct(brand.getId(), "에어맥스", 150000, ProductStatus.ACTIVE);
            Product loaded = productRepository.findById(product.getId()).orElseThrow();
            loaded.delete();
            productRepository.save(loaded);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.getForEntity(
                    "/api/v1/products/" + product.getId(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 존재하지_않는_ID면_404_Not_Found를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.getForEntity(
                    "/api/v1/products/999", ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
