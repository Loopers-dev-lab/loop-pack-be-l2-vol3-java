package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.infrastructure.brand.BrandEntity;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private BrandEntity savedBrand;

    @BeforeEach
    void setUp() {
        savedBrand = brandJpaRepository.save(createBrandEntity("나이키", "스포츠 브랜드"));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("GET /api/v1/products/{productId}")
    class GetProduct {

        @Test
        @DisplayName("존재하는 상품 ID로 조회하면, 상품 상세 정보를 반환한다")
        void success_get_product() {
            ProductEntity product = productJpaRepository.save(
                createProductEntity("에어맥스", savedBrand.getId(), 150000, 100, 10)
            );

            String url = "/api/v1/products/" + product.getId();
            var responseType = new ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductDetailResponse>>() {};

            ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> response =
                testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null), responseType);

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(product.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키")
            );
        }

        @Test
        @DisplayName("존재하지 않는 상품 ID로 조회하면, 400 응답을 반환한다")
        void fail_when_product_not_found() {
            String url = "/api/v1/products/99999";
            var responseType = new ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductDetailResponse>>() {};

            ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> response =
                testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null), responseType);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products")
    class GetProducts {

        @Test
        @DisplayName("상품 목록을 조회한다")
        void success_get_products() {
            productJpaRepository.save(createProductEntity("상품1", savedBrand.getId(), 10000, 50, 5));
            productJpaRepository.save(createProductEntity("상품2", savedBrand.getId(), 20000, 30, 10));

            String url = "/api/v1/products";
            var responseType = new ParameterizedTypeReference<ApiResponse<List<ProductV1Dto.ProductResponse>>>() {};

            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductResponse>>> response =
                testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null), responseType);

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).hasSize(2)
            );
        }

        @Test
        @DisplayName("브랜드 ID로 필터링하여 조회한다")
        void success_get_products_by_brand() {
            BrandEntity anotherBrand = brandJpaRepository.save(createBrandEntity("아디다스", "독일 브랜드"));
            productJpaRepository.save(createProductEntity("나이키상품", savedBrand.getId(), 10000, 50, 5));
            productJpaRepository.save(createProductEntity("아디다스상품", anotherBrand.getId(), 20000, 30, 10));

            String url = "/api/v1/products?brandId=" + savedBrand.getId();
            var responseType = new ParameterizedTypeReference<ApiResponse<List<ProductV1Dto.ProductResponse>>>() {};

            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductResponse>>> response =
                testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null), responseType);

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).hasSize(1),
                () -> assertThat(response.getBody().data().get(0).name()).isEqualTo("나이키상품")
            );
        }

        @Test
        @DisplayName("가격 오름차순으로 정렬하여 조회한다")
        void success_get_products_sorted_by_price() {
            productJpaRepository.save(createProductEntity("비싼상품", savedBrand.getId(), 50000, 50, 5));
            productJpaRepository.save(createProductEntity("싼상품", savedBrand.getId(), 10000, 30, 10));

            String url = "/api/v1/products?sortType=PRICE_ASC";
            var responseType = new ParameterizedTypeReference<ApiResponse<List<ProductV1Dto.ProductResponse>>>() {};

            ResponseEntity<ApiResponse<List<ProductV1Dto.ProductResponse>>> response =
                testRestTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(null), responseType);

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().get(0).name()).isEqualTo("싼상품"),
                () -> assertThat(response.getBody().data().get(1).name()).isEqualTo("비싼상품")
            );
        }
    }

    private BrandEntity createBrandEntity(String name, String description) {
        try {
            BrandEntity entity = BrandEntity.class.getDeclaredConstructor().newInstance();

            var nameField = BrandEntity.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(entity, name);

            var descField = BrandEntity.class.getDeclaredField("description");
            descField.setAccessible(true);
            descField.set(entity, description);

            return entity;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private ProductEntity createProductEntity(String name, Long brandId, int price, int stock, int likeCount) {
        try {
            ProductEntity entity = ProductEntity.class.getDeclaredConstructor().newInstance();

            var nameField = ProductEntity.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(entity, name);

            var brandIdField = ProductEntity.class.getDeclaredField("refBrandId");
            brandIdField.setAccessible(true);
            brandIdField.set(entity, brandId);

            var priceField = ProductEntity.class.getDeclaredField("price");
            priceField.setAccessible(true);
            priceField.set(entity, price);

            var stockField = ProductEntity.class.getDeclaredField("stock");
            stockField.setAccessible(true);
            stockField.set(entity, stock);

            var likeCountField = ProductEntity.class.getDeclaredField("likeCount");
            likeCountField.setAccessible(true);
            likeCountField.set(entity, likeCount);

            return entity;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
