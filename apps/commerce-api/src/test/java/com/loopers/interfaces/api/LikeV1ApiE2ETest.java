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
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final ProductJpaRepository productJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final LikeJpaRepository likeJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public LikeV1ApiE2ETest(
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

    @DisplayName("POST /api/v1/products/{productId}/likes - 좋아요 등록")
    @Nested
    class Like {

        @DisplayName("정상적인 정보가 주어지면, 좋아요가 생성된다.")
        @Test
        void createsLike_whenValidInfoIsProvided() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE));
            LikeRequest request = new LikeRequest(1L);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                "/api/v1/products/" + product.getId() + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(likeJpaRepository.countByProductId(product.getId())).isEqualTo(1L);
        }

        @DisplayName("이미 좋아요한 상품이면, CONFLICT 응답을 받는다.")
        @Test
        void returnsConflict_whenAlreadyLiked() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE));
            likeJpaRepository.save(new LikeModel(1L, product));
            LikeRequest request = new LikeRequest(1L);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                "/api/v1/products/" + product.getId() + "/likes",
                HttpMethod.POST,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("존재하지 않는 상품이면, NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            // arrange
            LikeRequest request = new LikeRequest(1L);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                "/api/v1/products/999/likes",
                HttpMethod.POST,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes - 좋아요 취소")
    @Nested
    class Unlike {

        @DisplayName("좋아요가 존재하면, 좋아요가 취소된다.")
        @Test
        void deletesLike_whenLikeExists() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE));
            likeJpaRepository.save(new LikeModel(1L, product));
            LikeRequest request = new LikeRequest(1L);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                "/api/v1/products/" + product.getId() + "/likes",
                HttpMethod.DELETE,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(likeJpaRepository.countByProductId(product.getId())).isEqualTo(0L);
        }

        @DisplayName("좋아요가 존재하지 않으면, NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenLikeDoesNotExist() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product = productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE));
            LikeRequest request = new LikeRequest(1L);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                "/api/v1/products/" + product.getId() + "/likes",
                HttpMethod.DELETE,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/users/{userId}/likes - 내가 좋아요한 상품 목록 조회")
    @Nested
    class GetMyLikes {

        @DisplayName("좋아요한 상품이 있으면, 상품 목록을 반환한다.")
        @Test
        void returnsLikedProducts_whenLikesExist() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product1 = productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE));
            ProductModel product2 = productJpaRepository.save(new ProductModel(brand, "에어포스", 120000L, "나이키 에어포스", 50, ProductStatus.ON_SALE));
            likeJpaRepository.save(new LikeModel(1L, product1));
            likeJpaRepository.save(new LikeModel(1L, product2));

            // act
            ParameterizedTypeReference<ApiResponse<List<Map<String, Object>>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = testRestTemplate.exchange(
                "/api/v1/users/1/likes",
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).hasSize(2)
            );
        }

        @DisplayName("좋아요한 상품이 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoLikesExist() {
            // act
            ParameterizedTypeReference<ApiResponse<List<Map<String, Object>>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = testRestTemplate.exchange(
                "/api/v1/users/1/likes",
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).isEmpty()
            );
        }

        @DisplayName("삭제된 상품은 좋아요 목록에 포함되지 않는다.")
        @Test
        void excludesDeletedProducts() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            ProductModel product1 = productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "나이키 에어맥스", 100, ProductStatus.ON_SALE));
            ProductModel product2 = new ProductModel(brand, "삭제상품", 100000L, "삭제될 상품", 10, ProductStatus.ON_SALE);
            product2.delete();
            product2 = productJpaRepository.save(product2);
            likeJpaRepository.save(new LikeModel(1L, product1));
            likeJpaRepository.save(new LikeModel(1L, product2));

            // act
            ParameterizedTypeReference<ApiResponse<List<Map<String, Object>>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<List<Map<String, Object>>>> response = testRestTemplate.exchange(
                "/api/v1/users/1/likes",
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).hasSize(1)
            );
        }
    }

    record LikeRequest(
        Long userId
    ) {}
}
