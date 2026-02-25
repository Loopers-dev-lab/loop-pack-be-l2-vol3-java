package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStatus;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String ENDPOINT_PRODUCTS = "/api/v1/products";

    private final TestRestTemplate testRestTemplate;
    private final ProductJpaRepository productJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final LikeJpaRepository likeJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        ProductJpaRepository productJpaRepository,
        BrandJpaRepository brandJpaRepository,
        LikeJpaRepository likeJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.productJpaRepository = productJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.likeJpaRepository = likeJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/products/{productId} - 상품 상세 조회")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품 ID가 주어지면, 브랜드 정보와 좋아요 수를 포함한 상품 정보를 반환한다.")
        @Test
        void returnsProductWithBrandAndLikeCount_whenIdExists() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE));
            likeJpaRepository.save(new LikeModel(1L, product));
            likeJpaRepository.save(new LikeModel(2L, product));

            // act
            ParameterizedTypeReference<ApiResponse<ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "/" + product.getId(),
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().likeCount()).isEqualTo(2L)
            );
        }

        @DisplayName("존재하지 않는 상품 ID가 주어지면, NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenIdDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "/999",
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("삭제된 상품 ID가 주어지면, NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenProductIsDeleted() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE);
            product.delete();
            productJpaRepository.save(product);

            // act
            ParameterizedTypeReference<ApiResponse<ProductResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "/" + product.getId(),
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/products - 상품 목록 조회")
    @Nested
    class GetAll {

        @DisplayName("기본 정렬(latest)로 조회하면, 최신순으로 상품 목록을 반환한다.")
        @Test
        void returnsProductListSortedByLatest() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE));
            productJpaRepository.save(new ProductModel(brand, "에어포스", 120000L, "나이키 에어포스", 50, ProductStatus.ON_SALE));

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "?page=0&size=20&sort=latest",
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat((List<?>) response.getBody().data().get("content")).hasSize(2)
            );
        }

        @DisplayName("price_asc 정렬로 조회하면, 가격 오름차순으로 상품 목록을 반환한다.")
        @Test
        void returnsProductListSortedByPriceAsc() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE));
            productJpaRepository.save(new ProductModel(brand, "에어포스", 120000L, "나이키 에어포스", 50, ProductStatus.ON_SALE));

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "?page=0&size=20&sort=price_asc",
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().data().get("content");
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(content).hasSize(2),
                () -> assertThat(((Number) content.get(0).get("price")).longValue()).isEqualTo(120000L),
                () -> assertThat(((Number) content.get(1).get("price")).longValue()).isEqualTo(150000L)
            );
        }

        @DisplayName("likes_desc 정렬로 조회하면, 좋아요 수 내림차순으로 상품 목록을 반환한다.")
        @Test
        void returnsProductListSortedByLikesDesc() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product1 = productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE));
            ProductModel product2 = productJpaRepository.save(new ProductModel(brand, "에어포스", 120000L, "나이키 에어포스", 50, ProductStatus.ON_SALE));

            // product2에 좋아요 3개, product1에 좋아요 1개
            likeJpaRepository.save(new LikeModel(1L, product2));
            likeJpaRepository.save(new LikeModel(2L, product2));
            likeJpaRepository.save(new LikeModel(3L, product2));
            likeJpaRepository.save(new LikeModel(1L, product1));

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "?page=0&size=20&sort=likes_desc",
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().data().get("content");
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(content).hasSize(2),
                () -> assertThat(content.get(0).get("name")).isEqualTo("에어포스"),
                () -> assertThat(content.get(1).get("name")).isEqualTo("에어맥스")
            );
        }
    }

    record ProductResponse(
        Long id,
        Long brandId,
        String brandName,
        String name,
        Long price,
        String description,
        int stockQuantity,
        String status,
        long likeCount,
        String createdAt,
        String updatedAt
    ) {}
}
