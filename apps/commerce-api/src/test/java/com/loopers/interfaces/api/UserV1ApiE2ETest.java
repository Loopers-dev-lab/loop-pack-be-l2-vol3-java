package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.productlike.ProductLike;
import com.loopers.domain.productlike.ProductLikeRepository;
import com.loopers.interfaces.api.product.ProductV1Dto;
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
@DisplayName("User Customer API E2E 테스트")
class UserV1ApiE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductLikeRepository productLikeRepository;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("GET /api/v1/users/me/likes - 내가 좋아요한 상품 목록")
    class GetLikedProducts {

        @Test
        @DisplayName("성공: 내가 좋아요한 상품 목록을 조회한다")
        void getLikedProducts_Success() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            Product product1 = productRepository.save(Product.create(brand.getId(), "상품1", null, new BigDecimal("10000"), 10, null));
            Product product2 = productRepository.save(Product.create(brand.getId(), "상품2", null, new BigDecimal("20000"), 20, null));
            Product product3 = productRepository.save(Product.create(brand.getId(), "상품3", null, new BigDecimal("30000"), 30, null));
            
            Long userId = 1L;

            // 상품1, 상품2에 좋아요
            productLikeRepository.save(ProductLike.create(userId, product1.getId()));
            productLikeRepository.save(ProductLike.create(userId, product2.getId()));

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // When
            ResponseEntity<ApiResponse<ProductV1Dto.PageResponse>> response = restTemplate.exchange(
                    "/api/v1/users/me/likes?page=0&size=10",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            ProductV1Dto.PageResponse data = response.getBody().data();
            assertThat(data.content()).hasSize(2);
            assertThat(data.totalElements()).isEqualTo(2);
            
            // 좋아요한 상품만 포함되어야 함
            assertThat(data.content()).extracting(ProductV1Dto.Response::id)
                    .containsExactlyInAnyOrder(product1.getId(), product2.getId());
        }

        @Test
        @DisplayName("성공: 좋아요한 상품이 없으면 빈 목록을 반환한다")
        void getLikedProducts_Empty() {
            // Given
            Long userId = 1L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // When
            ResponseEntity<ApiResponse<ProductV1Dto.PageResponse>> response = restTemplate.exchange(
                    "/api/v1/users/me/likes?page=0&size=10",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            ProductV1Dto.PageResponse data = response.getBody().data();
            assertThat(data.content()).isEmpty();
            assertThat(data.totalElements()).isEqualTo(0);
        }

        @Test
        @DisplayName("성공: 페이지네이션이 정상 동작한다")
        void getLikedProducts_Pagination() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            Long userId = 1L;

            // 5개 상품에 좋아요
            for (int i = 1; i <= 5; i++) {
                Product product = productRepository.save(Product.create(brand.getId(), "상품" + i, null, new BigDecimal("10000"), 10, null));
                productLikeRepository.save(ProductLike.create(userId, product.getId()));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // When - 첫 페이지 (size=2)
            ResponseEntity<ApiResponse<ProductV1Dto.PageResponse>> response = restTemplate.exchange(
                    "/api/v1/users/me/likes?page=0&size=2",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            ProductV1Dto.PageResponse data = response.getBody().data();
            assertThat(data.content()).hasSize(2);
            assertThat(data.totalElements()).isEqualTo(5);
            assertThat(data.totalPages()).isEqualTo(3);
            assertThat(data.number()).isEqualTo(0);
        }
    }
}
