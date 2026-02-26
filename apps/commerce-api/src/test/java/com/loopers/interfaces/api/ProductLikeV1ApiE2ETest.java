package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.interfaces.api.productlike.ProductLikeV1Dto;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("ProductLike Customer API E2E 테스트")
class ProductLikeV1ApiE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api/v1/products/{productId}/likes - 좋아요 등록")
    class RegisterLike {

        @Test
        @DisplayName("성공: 상품에 좋아요를 등록한다")
        void registerLike_Success() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            Product product = productRepository.save(Product.create(brand.getId(), "상품명", null, new BigDecimal("10000"), 10, null));
            Long userId = 1L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // When
            ResponseEntity<ApiResponse<ProductLikeV1Dto.Response>> response = restTemplate.exchange(
                    "/api/v1/products/" + product.getId() + "/likes",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            ProductLikeV1Dto.Response data = response.getBody().data();
            assertThat(data.id()).isNotNull();
            assertThat(data.userId()).isEqualTo(userId);
            assertThat(data.productId()).isEqualTo(product.getId());
            assertThat(data.createdAt()).isNotNull();

            // 상품의 likesCount 확인
            Product updatedProduct = productRepository.findActiveById(product.getId()).get();
            assertThat(updatedProduct.getLikesCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 상품에 좋아요하면 404 NOT_FOUND를 반환한다")
        void registerLike_ProductNotFound() {
            // Given
            Long userId = 1L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api/v1/products/999/likes",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 이미 좋아요한 상품이면 409 CONFLICT를 반환한다")
        void registerLike_AlreadyLiked() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            Product product = productRepository.save(Product.create(brand.getId(), "상품명", null, new BigDecimal("10000"), 10, null));
            Long userId = 1L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // 첫 번째 좋아요
            restTemplate.exchange(
                    "/api/v1/products/" + product.getId() + "/likes",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<ProductLikeV1Dto.Response>>() {}
            );

            // When - 두 번째 좋아요 시도
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api/v1/products/" + product.getId() + "/likes",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/products/{productId}/likes - 좋아요 취소")
    class CancelLike {

        @Test
        @DisplayName("성공: 좋아요를 취소한다")
        void cancelLike_Success() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            Product product = productRepository.save(Product.create(brand.getId(), "상품명", null, new BigDecimal("10000"), 10, null));
            Long userId = 1L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // 좋아요 등록
            restTemplate.exchange(
                    "/api/v1/products/" + product.getId() + "/likes",
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<ProductLikeV1Dto.Response>>() {}
            );

            // When - 좋아요 취소
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api/v1/products/" + product.getId() + "/likes",
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 상품의 likesCount 확인
            Product updatedProduct = productRepository.findActiveById(product.getId()).get();
            assertThat(updatedProduct.getLikesCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 상품의 좋아요를 취소하면 404 NOT_FOUND를 반환한다")
        void cancelLike_ProductNotFound() {
            // Given
            Long userId = 1L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api/v1/products/999/likes",
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 좋아요하지 않은 상품의 좋아요를 취소하면 404 NOT_FOUND를 반환한다")
        void cancelLike_NotLiked() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            Product product = productRepository.save(Product.create(brand.getId(), "상품명", null, new BigDecimal("10000"), 10, null));
            Long userId = 1L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api/v1/products/" + product.getId() + "/likes",
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
