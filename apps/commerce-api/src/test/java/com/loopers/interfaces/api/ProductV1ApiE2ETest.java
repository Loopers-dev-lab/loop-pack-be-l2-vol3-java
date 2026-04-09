package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStatus;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final LikeService likeService;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public ProductV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        ProductJpaRepository productJpaRepository,
        BrandJpaRepository brandJpaRepository,
        LikeService likeService,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.productJpaRepository = productJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.likeService = likeService;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
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
            likeService.like(1L, product.getId());
            likeService.like(2L, product.getId());

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

    @DisplayName("POST /api/v1/products/{productId}/dwell - 상품 체류 시간 보고")
    @Nested
    class ReportDwell {

        @DisplayName("5초 이상의 체류 시간을 보고하면, 200 OK 응답을 받는다.")
        @Test
        void returnsOk_whenDwellTimeIsValid() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("dwellTimeSeconds", 30), headers);

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "/" + product.getId() + "/dwell",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("5초 미만의 체류 시간을 보고하면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenDwellTimeIsLessThanMinimum() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("dwellTimeSeconds", 3), headers);

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "/" + product.getId() + "/dwell",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
            likeService.like(1L, product2.getId());
            likeService.like(2L, product2.getId());
            likeService.like(3L, product2.getId());
            likeService.like(1L, product1.getId());

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

        @DisplayName("brandId 필터와 likes_desc 정렬을 함께 조회하면, 해당 브랜드 내 좋아요 순으로 반환한다.")
        @Test
        void returnsProductListFilteredByBrandAndSortedByLikesDesc() {
            // arrange
            BrandModel nike = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            BrandModel adidas = brandJpaRepository.save(new BrandModel("아디다스", "스포츠 의류 및 신발 브랜드"));
            ProductModel nike1 = productJpaRepository.save(new ProductModel(nike, "나이키-1", 100000L, "desc", 100, ProductStatus.ON_SALE));
            ProductModel nike2 = productJpaRepository.save(new ProductModel(nike, "나이키-2", 120000L, "desc", 100, ProductStatus.ON_SALE));
            ProductModel adidas1 = productJpaRepository.save(new ProductModel(adidas, "아디다스-1", 110000L, "desc", 100, ProductStatus.ON_SALE));
            likeService.like(10L, nike2.getId());
            likeService.like(11L, nike2.getId());
            likeService.like(20L, adidas1.getId());

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "?page=0&size=20&sort=likes_desc&brandId=" + nike.getId(),
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().data().get("content");
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(content).hasSize(2),
                () -> assertThat(content.get(0).get("name")).isEqualTo("나이키-2"),
                () -> assertThat(content.get(1).get("name")).isEqualTo("나이키-1")
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
        String updatedAt,
        Long ranking
    ) {}
}
