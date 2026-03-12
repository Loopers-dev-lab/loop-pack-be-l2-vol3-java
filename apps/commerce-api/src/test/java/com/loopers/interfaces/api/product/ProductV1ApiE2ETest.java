package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.brand.admin.BrandAdminV1Dto;
import com.loopers.interfaces.api.product.admin.ProductAdminV1Dto;
import com.loopers.utils.DatabaseCleanUp;
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
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String CUSTOMER_ENDPOINT = "/api/v1/products";
    private static final String ADMIN_ENDPOINT = "/api-admin/v1/products";
    private static final String BRAND_ADMIN_ENDPOINT = "/api-admin/v1/brands";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    private Long createBrand(String name) {
        BrandAdminV1Dto.CreateRequest request = new BrandAdminV1Dto.CreateRequest(name, "설명");
        ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
            BRAND_ADMIN_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    private Long createProduct(String name, int price, Long brandId, int initialStock) {
        ProductAdminV1Dto.CreateRequest request = new ProductAdminV1Dto.CreateRequest(name, "설명", price, brandId, initialStock);
        ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = testRestTemplate.exchange(
            ADMIN_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    private void deleteProduct(Long productId) {
        testRestTemplate.exchange(
            ADMIN_ENDPOINT + "/" + productId, HttpMethod.DELETE, new HttpEntity<>(null, adminHeaders()),
            new ParameterizedTypeReference<ApiResponse<Object>>() {}
        );
    }

    private void deleteBrand(Long brandId) {
        testRestTemplate.exchange(
            BRAND_ADMIN_ENDPOINT + "/" + brandId, HttpMethod.DELETE, new HttpEntity<>(null, adminHeaders()),
            new ParameterizedTypeReference<ApiResponse<Object>>() {}
        );
    }

    // ========== Customer API ==========

    @DisplayName("GET /api/v1/products")
    @Nested
    class CustomerGetProducts {

        @DisplayName("상품 목록을 조회하면 200과 재고상태를 포함하여 반환한다")
        @Test
        void returns200WithStockStatus() {
            // given
            Long brandId = createBrand("나이키");
            createProduct("에어맥스 90", 129000, brandId, 100);

            // when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                CUSTOMER_ENDPOINT + "?page=0&size=10", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("brandId로 필터링하여 조회한다")
        @Test
        void filtersByBrandId() {
            // given
            Long nikeId = createBrand("나이키");
            Long adidasId = createBrand("아디다스");
            createProduct("에어맥스 90", 129000, nikeId, 100);
            createProduct("슈퍼스타", 99000, adidasId, 50);

            // when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                CUSTOMER_ENDPOINT + "?brandId=" + nikeId + "&page=0&size=10", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class CustomerGetProduct {

        @DisplayName("존재하는 상품을 조회하면 200과 재고상태를 포함하여 반환한다")
        @Test
        void returns200WithStockStatus() {
            // given
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스 90", 129000, brandId, 100);

            // when
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                CUSTOMER_ENDPOINT + "/" + productId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(productId),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스 90"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(129000),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().stockStatus()).isNotNull()
            );
        }

        @DisplayName("삭제된 상품을 조회하면 404를 반환한다")
        @Test
        void returns404WhenDeleted() {
            // given
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스 90", 129000, brandId, 100);
            deleteProduct(productId);

            // when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                CUSTOMER_ENDPOINT + "/" + productId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("미존재 상품을 조회하면 404를 반환한다")
        @Test
        void returns404WhenNotFound() {
            // given & when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                CUSTOMER_ENDPOINT + "/999", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ========== Admin API ==========

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    class AdminCreate {

        @DisplayName("유효한 정보로 등록하면 200과 재고수량을 포함하여 반환한다")
        @Test
        void returns200WithStockQuantity() {
            // given
            Long brandId = createBrand("나이키");
            ProductAdminV1Dto.CreateRequest request = new ProductAdminV1Dto.CreateRequest(
                "에어맥스 90", "러닝화", 129000, brandId, 100
            );

            // when
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스 90"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(129000),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(100),
                () -> assertThat(response.getBody().data().id()).isNotNull()
            );
        }

        @DisplayName("삭제된 브랜드에 등록하면 404를 반환한다")
        @Test
        void returns404WhenBrandDeleted() {
            // given
            Long brandId = createBrand("나이키");
            deleteBrand(brandId);
            ProductAdminV1Dto.CreateRequest request = new ProductAdminV1Dto.CreateRequest(
                "에어맥스 90", "러닝화", 129000, brandId, 100
            );

            // when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    class AdminGetAll {

        @DisplayName("삭제 포함하여 목록을 반환한다")
        @Test
        void returns200IncludingDeleted() {
            // given
            Long brandId = createBrand("나이키");
            createProduct("에어맥스 90", 129000, brandId, 100);
            Long deletedId = createProduct("삭제될 상품", 99000, brandId, 10);
            deleteProduct(deletedId);

            // when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT + "?page=0&size=10", HttpMethod.GET, new HttpEntity<>(null, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }
    }

    @DisplayName("GET /api-admin/v1/products/{productId}")
    @Nested
    class AdminGetProduct {

        @DisplayName("존재하는 상품을 조회하면 200을 반환한다")
        @Test
        void returns200() {
            // given
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스 90", 129000, brandId, 100);

            // when
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT + "/" + productId, HttpMethod.GET, new HttpEntity<>(null, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스 90"),
                () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(100)
            );
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId}")
    @Nested
    class AdminUpdate {

        @DisplayName("수정 성공 시 200과 변경된 정보를 반환한다")
        @Test
        void returns200WithUpdatedInfo() {
            // given
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스 90", 129000, brandId, 100);
            ProductAdminV1Dto.UpdateRequest request = new ProductAdminV1Dto.UpdateRequest(
                "에어맥스 95", "뉴 러닝화", 159000
            );

            // when
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT + "/" + productId, HttpMethod.PUT, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스 95"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("뉴 러닝화"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(159000)
            );
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId}")
    @Nested
    class AdminDelete {

        @DisplayName("삭제 성공 시 200을 반환한다")
        @Test
        void returns200OnSuccess() {
            // given
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스 90", 129000, brandId, 100);

            // when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT + "/" + productId, HttpMethod.DELETE, new HttpEntity<>(null, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("미존재 상품 삭제 시 404를 반환한다")
        @Test
        void returns404WhenNotFound() {
            // given & when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT + "/999", HttpMethod.DELETE, new HttpEntity<>(null, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}
