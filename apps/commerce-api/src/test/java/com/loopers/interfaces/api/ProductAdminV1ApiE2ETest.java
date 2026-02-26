package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.interfaces.api.brand.BrandV1Dto;
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
@DisplayName("Product Admin V1 API E2E 테스트")
class ProductAdminV1ApiE2ETest {

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

    @Nested
    @DisplayName("POST /api-admin/v1/products - 상품 등록")
    class RegisterProduct {

        @Test
        @DisplayName("성공: 유효한 데이터로 상품을 등록한다")
        void registerProduct_Success() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", "프랑스 명품 브랜드", null));

            ProductV1Dto.RegisterRequest request = new ProductV1Dto.RegisterRequest(
                    brand.getId(),
                    "샤넬 No.5 향수",
                    "클래식한 샤넬의 시그니처 향수",
                    new BigDecimal("150000.00"),
                    100,
                    "https://example.com/chanel-no5.jpg"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");
            HttpEntity<ProductV1Dto.RegisterRequest> httpEntity = new HttpEntity<>(request, headers);

            // When
            ResponseEntity<ApiResponse<ProductV1Dto.Response>> response = restTemplate.exchange(
                    "/api-admin/v1/products",
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();

            ProductV1Dto.Response data = response.getBody().data();
            assertThat(data.id()).isNotNull();
            assertThat(data.brandId()).isEqualTo(brand.getId());
            assertThat(data.name()).isEqualTo("샤넬 No.5 향수");
            assertThat(data.description()).isEqualTo("클래식한 샤넬의 시그니처 향수");
            assertThat(data.price()).isEqualByComparingTo(new BigDecimal("150000.00"));
            assertThat(data.stock()).isEqualTo(100);
            assertThat(data.imageUrl()).isEqualTo("https://example.com/chanel-no5.jpg");
            assertThat(data.likesCount()).isEqualTo(0);
            assertThat(data.createdAt()).isNotNull();
            assertThat(data.updatedAt()).isNotNull();
        }

        @Test
        @DisplayName("실패: 브랜드가 존재하지 않으면 404 NOT_FOUND를 반환한다")
        void registerProduct_BrandNotFound() {
            // Given
            ProductV1Dto.RegisterRequest request = new ProductV1Dto.RegisterRequest(
                    999L,
                    "상품명",
                    null,
                    new BigDecimal("10000"),
                    10,
                    null
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");
            HttpEntity<ProductV1Dto.RegisterRequest> httpEntity = new HttpEntity<>(request, headers);

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api-admin/v1/products",
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 브랜드가 삭제되었으면 404 NOT_FOUND를 반환한다")
        void registerProduct_BrandDeleted() {
            // Given
            Brand brand = brandRepository.save(Brand.create("삭제될 브랜드", null, null));
            brand.delete();
            brandRepository.save(brand);

            ProductV1Dto.RegisterRequest request = new ProductV1Dto.RegisterRequest(
                    brand.getId(),
                    "상품명",
                    null,
                    new BigDecimal("10000"),
                    10,
                    null
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");
            HttpEntity<ProductV1Dto.RegisterRequest> httpEntity = new HttpEntity<>(request, headers);

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api-admin/v1/products",
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 상품명이 null이면 400 BAD_REQUEST를 반환한다")
        void registerProduct_NameNull() {
            // Given
            Brand brand = brandRepository.save(Brand.create("브랜드", null, null));

            ProductV1Dto.RegisterRequest request = new ProductV1Dto.RegisterRequest(
                    brand.getId(),
                    null,
                    null,
                    new BigDecimal("10000"),
                    10,
                    null
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");
            HttpEntity<ProductV1Dto.RegisterRequest> httpEntity = new HttpEntity<>(request, headers);

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api-admin/v1/products",
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("실패: 가격이 0 이하면 400 BAD_REQUEST를 반환한다")
        void registerProduct_PriceZero() {
            // Given
            Brand brand = brandRepository.save(Brand.create("브랜드", null, null));

            ProductV1Dto.RegisterRequest request = new ProductV1Dto.RegisterRequest(
                    brand.getId(),
                    "상품명",
                    null,
                    BigDecimal.ZERO,
                    10,
                    null
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");
            HttpEntity<ProductV1Dto.RegisterRequest> httpEntity = new HttpEntity<>(request, headers);

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api-admin/v1/products",
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("실패: 재고가 음수면 400 BAD_REQUEST를 반환한다")
        void registerProduct_StockNegative() {
            // Given
            Brand brand = brandRepository.save(Brand.create("브랜드", null, null));

            ProductV1Dto.RegisterRequest request = new ProductV1Dto.RegisterRequest(
                    brand.getId(),
                    "상품명",
                    null,
                    new BigDecimal("10000"),
                    -1,
                    null
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");
            HttpEntity<ProductV1Dto.RegisterRequest> httpEntity = new HttpEntity<>(request, headers);

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api-admin/v1/products",
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("GET /api-admin/v1/products - 상품 목록 조회")
    class GetAllProducts {

        @Test
        @DisplayName("성공: 페이지네이션으로 상품 목록을 조회한다")
        void getAllProducts_Success() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");
            
            // 상품 3개 등록
            for (int i = 1; i <= 3; i++) {
                ProductV1Dto.RegisterRequest request = new ProductV1Dto.RegisterRequest(
                        brand.getId(),
                        "상품" + i,
                        null,
                        new BigDecimal("10000"),
                        10,
                        null
                );
                restTemplate.exchange(
                        "/api-admin/v1/products",
                        HttpMethod.POST,
                        new HttpEntity<>(request, headers),
                        new ParameterizedTypeReference<ApiResponse<ProductV1Dto.Response>>() {}
                );
            }

            // When
            ResponseEntity<ApiResponse<ProductV1Dto.PageResponse>> response = restTemplate.exchange(
                    "/api-admin/v1/products?page=0&size=10",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            ProductV1Dto.PageResponse data = response.getBody().data();
            assertThat(data.content()).hasSize(3);
            assertThat(data.totalElements()).isEqualTo(3);
            assertThat(data.totalPages()).isEqualTo(1);
            assertThat(data.number()).isEqualTo(0);
            assertThat(data.size()).isEqualTo(10);
        }

        @Test
        @DisplayName("성공: 브랜드 필터로 상품 목록을 조회한다")
        void getAllProducts_WithBrandFilter() {
            // Given
            Brand brand1 = brandRepository.save(Brand.create("샤넬", null, null));
            Brand brand2 = brandRepository.save(Brand.create("디올", null, null));
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");
            
            // 샤넬 상품 2개
            for (int i = 1; i <= 2; i++) {
                ProductV1Dto.RegisterRequest request = new ProductV1Dto.RegisterRequest(
                        brand1.getId(),
                        "샤넬 상품" + i,
                        null,
                        new BigDecimal("10000"),
                        10,
                        null
                );
                restTemplate.exchange(
                        "/api-admin/v1/products",
                        HttpMethod.POST,
                        new HttpEntity<>(request, headers),
                        new ParameterizedTypeReference<ApiResponse<ProductV1Dto.Response>>() {}
                );
            }
            
            // 디올 상품 1개
            ProductV1Dto.RegisterRequest request = new ProductV1Dto.RegisterRequest(
                    brand2.getId(),
                    "디올 상품",
                    null,
                    new BigDecimal("20000"),
                    5,
                    null
            );
            restTemplate.exchange(
                    "/api-admin/v1/products",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<ApiResponse<ProductV1Dto.Response>>() {}
            );

            // When
            ResponseEntity<ApiResponse<ProductV1Dto.PageResponse>> response = restTemplate.exchange(
                    "/api-admin/v1/products?brandId=" + brand1.getId() + "&page=0&size=10",
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
            assertThat(data.content()).allMatch(p -> p.brandId().equals(brand1.getId()));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 브랜드로 필터링하면 404 NOT_FOUND를 반환한다")
        void getAllProducts_BrandNotFound() {
            // Given
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api-admin/v1/products?brandId=999&page=0&size=10",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("GET /api-admin/v1/products/{productId} - 상품 상세 조회")
    class GetProduct {

        @Test
        @DisplayName("성공: 유효한 상품 ID로 조회한다")
        void getProduct_Success() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            
            ProductV1Dto.RegisterRequest registerRequest = new ProductV1Dto.RegisterRequest(
                    brand.getId(),
                    "샤넬 No.5 향수",
                    "클래식한 향수",
                    new BigDecimal("150000"),
                    100,
                    "https://example.com/image.jpg"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");
            
            ResponseEntity<ApiResponse<ProductV1Dto.Response>> registerResponse = restTemplate.exchange(
                    "/api-admin/v1/products",
                    HttpMethod.POST,
                    new HttpEntity<>(registerRequest, headers),
                    new ParameterizedTypeReference<>() {}
            );
            
            Long productId = registerResponse.getBody().data().id();

            // When
            ResponseEntity<ApiResponse<ProductV1Dto.Response>> response = restTemplate.exchange(
                    "/api-admin/v1/products/" + productId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            ProductV1Dto.Response data = response.getBody().data();
            assertThat(data.id()).isEqualTo(productId);
            assertThat(data.name()).isEqualTo("샤넬 No.5 향수");
            assertThat(data.description()).isEqualTo("클래식한 향수");
            assertThat(data.price()).isEqualByComparingTo(new BigDecimal("150000"));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 상품 ID로 조회하면 404 NOT_FOUND를 반환한다")
        void getProduct_NotFound() {
            // Given
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api-admin/v1/products/999",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("PUT /api-admin/v1/products/{productId} - 상품 수정")
    class UpdateProduct {

        @Test
        @DisplayName("성공: 유효한 데이터로 상품을 수정한다")
        void updateProduct_Success() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            
            ProductV1Dto.RegisterRequest registerRequest = new ProductV1Dto.RegisterRequest(
                    brand.getId(),
                    "기존 상품명",
                    "기존 설명",
                    new BigDecimal("10000"),
                    10,
                    null
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");
            
            ResponseEntity<ApiResponse<ProductV1Dto.Response>> registerResponse = restTemplate.exchange(
                    "/api-admin/v1/products",
                    HttpMethod.POST,
                    new HttpEntity<>(registerRequest, headers),
                    new ParameterizedTypeReference<>() {}
            );
            
            Long productId = registerResponse.getBody().data().id();

            ProductV1Dto.UpdateRequest updateRequest = new ProductV1Dto.UpdateRequest(
                    "새 상품명",
                    "새 설명",
                    new BigDecimal("20000"),
                    20,
                    "https://example.com/new.jpg"
            );

            // When
            ResponseEntity<ApiResponse<ProductV1Dto.Response>> response = restTemplate.exchange(
                    "/api-admin/v1/products/" + productId,
                    HttpMethod.PUT,
                    new HttpEntity<>(updateRequest, headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            ProductV1Dto.Response data = response.getBody().data();
            assertThat(data.id()).isEqualTo(productId);
            assertThat(data.name()).isEqualTo("새 상품명");
            assertThat(data.description()).isEqualTo("새 설명");
            assertThat(data.price()).isEqualByComparingTo(new BigDecimal("20000"));
            assertThat(data.stock()).isEqualTo(20);
            assertThat(data.imageUrl()).isEqualTo("https://example.com/new.jpg");
        }

        @Test
        @DisplayName("성공: 일부 필드만 수정한다")
        void updateProduct_PartialUpdate() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            
            ProductV1Dto.RegisterRequest registerRequest = new ProductV1Dto.RegisterRequest(
                    brand.getId(),
                    "기존 상품명",
                    "기존 설명",
                    new BigDecimal("10000"),
                    10,
                    null
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");
            
            ResponseEntity<ApiResponse<ProductV1Dto.Response>> registerResponse = restTemplate.exchange(
                    "/api-admin/v1/products",
                    HttpMethod.POST,
                    new HttpEntity<>(registerRequest, headers),
                    new ParameterizedTypeReference<>() {}
            );
            
            Long productId = registerResponse.getBody().data().id();

            ProductV1Dto.UpdateRequest updateRequest = new ProductV1Dto.UpdateRequest(
                    "새 상품명",
                    null,
                    null,
                    null,
                    null
            );

            // When
            ResponseEntity<ApiResponse<ProductV1Dto.Response>> response = restTemplate.exchange(
                    "/api-admin/v1/products/" + productId,
                    HttpMethod.PUT,
                    new HttpEntity<>(updateRequest, headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            ProductV1Dto.Response data = response.getBody().data();
            assertThat(data.name()).isEqualTo("새 상품명");
            assertThat(data.description()).isEqualTo("기존 설명");
            assertThat(data.price()).isEqualByComparingTo(new BigDecimal("10000"));
        }

        @Test
        @DisplayName("실패: 존재하지 않는 상품 ID로 수정하면 404 NOT_FOUND를 반환한다")
        void updateProduct_NotFound() {
            // Given
            ProductV1Dto.UpdateRequest updateRequest = new ProductV1Dto.UpdateRequest(
                    "새 상품명",
                    null,
                    null,
                    null,
                    null
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api-admin/v1/products/999",
                    HttpMethod.PUT,
                    new HttpEntity<>(updateRequest, headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 가격이 0 이하면 400 BAD_REQUEST를 반환한다")
        void updateProduct_PriceZero() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            
            ProductV1Dto.RegisterRequest registerRequest = new ProductV1Dto.RegisterRequest(
                    brand.getId(),
                    "상품명",
                    null,
                    new BigDecimal("10000"),
                    10,
                    null
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");
            
            ResponseEntity<ApiResponse<ProductV1Dto.Response>> registerResponse = restTemplate.exchange(
                    "/api-admin/v1/products",
                    HttpMethod.POST,
                    new HttpEntity<>(registerRequest, headers),
                    new ParameterizedTypeReference<>() {}
            );
            
            Long productId = registerResponse.getBody().data().id();

            ProductV1Dto.UpdateRequest updateRequest = new ProductV1Dto.UpdateRequest(
                    null,
                    null,
                    BigDecimal.ZERO,
                    null,
                    null
            );

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api-admin/v1/products/" + productId,
                    HttpMethod.PUT,
                    new HttpEntity<>(updateRequest, headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("DELETE /api-admin/v1/products/{productId} - 상품 삭제")
    class DeleteProduct {

        @Test
        @DisplayName("성공: 유효한 상품 ID로 삭제한다")
        void deleteProduct_Success() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            
            ProductV1Dto.RegisterRequest registerRequest = new ProductV1Dto.RegisterRequest(
                    brand.getId(),
                    "상품명",
                    null,
                    new BigDecimal("10000"),
                    10,
                    null
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");
            
            ResponseEntity<ApiResponse<ProductV1Dto.Response>> registerResponse = restTemplate.exchange(
                    "/api-admin/v1/products",
                    HttpMethod.POST,
                    new HttpEntity<>(registerRequest, headers),
                    new ParameterizedTypeReference<>() {}
            );
            
            Long productId = registerResponse.getBody().data().id();

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api-admin/v1/products/" + productId,
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 삭제 후 조회 시 404
            ResponseEntity<ApiResponse<ProductV1Dto.Response>> getResponse = restTemplate.exchange(
                    "/api-admin/v1/products/" + productId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 상품 ID로 삭제하면 404 NOT_FOUND를 반환한다")
        void deleteProduct_NotFound() {
            // Given
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "loopers.admin");

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api-admin/v1/products/999",
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
