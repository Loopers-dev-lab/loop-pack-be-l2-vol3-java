package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
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
@DisplayName("Product Customer API E2E 테스트")
class ProductV1ApiE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private BrandRepository brandRepository;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long registerProduct(Long brandId, String name, BigDecimal price, Integer stock) {
        ProductV1Dto.RegisterRequest request = new ProductV1Dto.RegisterRequest(
                brandId,
                name,
                null,
                price,
                stock,
                null
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");

        ResponseEntity<ApiResponse<ProductV1Dto.Response>> response = restTemplate.exchange(
                "/api-admin/v1/products",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
        );

        return response.getBody().data().id();
    }

    @Nested
    @DisplayName("GET /api/v1/products - 고객 상품 목록 조회")
    class GetAllProducts {

        @Test
        @DisplayName("성공: 페이지네이션으로 상품 목록을 조회한다")
        void getAllProducts_Success() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            
            // 상품 3개 등록
            for (int i = 1; i <= 3; i++) {
                registerProduct(brand.getId(), "상품" + i, new BigDecimal("10000"), 10);
            }

            // When
            ResponseEntity<ApiResponse<ProductV1Dto.PageResponse>> response = restTemplate.exchange(
                    "/api/v1/products?page=0&size=10",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            ProductV1Dto.PageResponse data = response.getBody().data();
            assertThat(data.content()).hasSize(3);
            assertThat(data.totalElements()).isEqualTo(3);
        }

        @Test
        @DisplayName("성공: 브랜드 필터로 상품 목록을 조회한다")
        void getAllProducts_WithBrandFilter() {
            // Given
            Brand brand1 = brandRepository.save(Brand.create("샤넬", null, null));
            Brand brand2 = brandRepository.save(Brand.create("디올", null, null));
            
            // 샤넬 상품 2개
            registerProduct(brand1.getId(), "샤넬 상품1", new BigDecimal("10000"), 10);
            registerProduct(brand1.getId(), "샤넬 상품2", new BigDecimal("20000"), 20);
            
            // 디올 상품 1개
            registerProduct(brand2.getId(), "디올 상품", new BigDecimal("30000"), 30);

            // When
            ResponseEntity<ApiResponse<ProductV1Dto.PageResponse>> response = restTemplate.exchange(
                    "/api/v1/products?brandId=" + brand1.getId() + "&page=0&size=10",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            ProductV1Dto.PageResponse data = response.getBody().data();
            assertThat(data.content()).hasSize(2);
            assertThat(data.content()).allMatch(p -> p.brandId().equals(brand1.getId()));
        }

        @Test
        @DisplayName("성공: latest 정렬로 상품 목록을 조회한다 (최신순)")
        void getAllProducts_SortByLatest() throws InterruptedException {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            
            Long productId1 = registerProduct(brand.getId(), "상품1", new BigDecimal("10000"), 10);
            Thread.sleep(100);
            Long productId2 = registerProduct(brand.getId(), "상품2", new BigDecimal("20000"), 20);
            Thread.sleep(100);
            Long productId3 = registerProduct(brand.getId(), "상품3", new BigDecimal("30000"), 30);

            // When
            ResponseEntity<ApiResponse<ProductV1Dto.PageResponse>> response = restTemplate.exchange(
                    "/api/v1/products?sort=latest&page=0&size=10",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            ProductV1Dto.PageResponse data = response.getBody().data();
            assertThat(data.content()).hasSize(3);
            // 최신순: 3 -> 2 -> 1
            assertThat(data.content().get(0).id()).isEqualTo(productId3);
            assertThat(data.content().get(1).id()).isEqualTo(productId2);
            assertThat(data.content().get(2).id()).isEqualTo(productId1);
        }

        @Test
        @DisplayName("성공: price_asc 정렬로 상품 목록을 조회한다 (가격 낮은순)")
        void getAllProducts_SortByPriceAsc() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            
            Long productId1 = registerProduct(brand.getId(), "상품1", new BigDecimal("30000"), 10);
            Long productId2 = registerProduct(brand.getId(), "상품2", new BigDecimal("10000"), 20);
            Long productId3 = registerProduct(brand.getId(), "상품3", new BigDecimal("20000"), 30);

            // When
            ResponseEntity<ApiResponse<ProductV1Dto.PageResponse>> response = restTemplate.exchange(
                    "/api/v1/products?sort=price_asc&page=0&size=10",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            ProductV1Dto.PageResponse data = response.getBody().data();
            assertThat(data.content()).hasSize(3);
            // 가격 낮은순: 10000 -> 20000 -> 30000
            assertThat(data.content().get(0).price()).isEqualByComparingTo(new BigDecimal("10000"));
            assertThat(data.content().get(1).price()).isEqualByComparingTo(new BigDecimal("20000"));
            assertThat(data.content().get(2).price()).isEqualByComparingTo(new BigDecimal("30000"));
        }

        @Test
        @DisplayName("성공: likes_desc 정렬로 상품 목록을 조회한다 (좋아요 많은순)")
        void getAllProducts_SortByLikesDesc() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            
            // 상품 등록 (likesCount는 기본 0)
            registerProduct(brand.getId(), "상품1", new BigDecimal("10000"), 10);
            registerProduct(brand.getId(), "상품2", new BigDecimal("20000"), 20);
            registerProduct(brand.getId(), "상품3", new BigDecimal("30000"), 30);

            // When
            ResponseEntity<ApiResponse<ProductV1Dto.PageResponse>> response = restTemplate.exchange(
                    "/api/v1/products?sort=likes_desc&page=0&size=10",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            ProductV1Dto.PageResponse data = response.getBody().data();
            assertThat(data.content()).hasSize(3);
            // 모두 likesCount가 0이므로 순서는 보장되지 않지만 조회는 성공
            assertThat(data.content()).allMatch(p -> p.likesCount() == 0);
        }

        @Test
        @DisplayName("성공: 삭제된 상품은 목록에서 제외된다")
        void getAllProducts_ExcludeDeletedProducts() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            
            Long productId1 = registerProduct(brand.getId(), "상품1", new BigDecimal("10000"), 10);
            Long productId2 = registerProduct(brand.getId(), "상품2", new BigDecimal("20000"), 20);
            
            // 상품1 삭제
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");
            restTemplate.exchange(
                    "/api-admin/v1/products/" + productId1,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // When
            ResponseEntity<ApiResponse<ProductV1Dto.PageResponse>> response = restTemplate.exchange(
                    "/api/v1/products?page=0&size=10",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            ProductV1Dto.PageResponse data = response.getBody().data();
            assertThat(data.content()).hasSize(1);
            assertThat(data.content().get(0).id()).isEqualTo(productId2);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/products/{productId} - 고객 상품 상세 조회")
    class GetProduct {

        @Test
        @DisplayName("성공: 유효한 상품 ID로 조회한다")
        void getProduct_Success() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            Long productId = registerProduct(brand.getId(), "샤넬 No.5 향수", new BigDecimal("150000"), 100);

            // When
            ResponseEntity<ApiResponse<ProductV1Dto.Response>> response = restTemplate.exchange(
                    "/api/v1/products/" + productId,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            ProductV1Dto.Response data = response.getBody().data();
            assertThat(data.id()).isEqualTo(productId);
            assertThat(data.name()).isEqualTo("샤넬 No.5 향수");
            assertThat(data.price()).isEqualByComparingTo(new BigDecimal("150000"));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 상품 ID로 조회하면 404 NOT_FOUND를 반환한다")
        void getProduct_NotFound() {
            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api/v1/products/999",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 삭제된 상품은 조회할 수 없다")
        void getProduct_Deleted() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            Long productId = registerProduct(brand.getId(), "상품명", new BigDecimal("10000"), 10);
            
            // 상품 삭제
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");
            restTemplate.exchange(
                    "/api-admin/v1/products/" + productId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api/v1/products/" + productId,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
