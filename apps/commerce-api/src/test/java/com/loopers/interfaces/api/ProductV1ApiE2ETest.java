package com.loopers.interfaces.api;

import com.loopers.interfaces.api.brand.AdminBrandV1Dto;
import com.loopers.interfaces.api.product.AdminProductV1Dto;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.utils.DatabaseCleanUp;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/products";
    private static final String ADMIN_BRAND_ENDPOINT = "/api-admin/v1/brands";
    private static final String ADMIN_PRODUCT_ENDPOINT = "/api-admin/v1/products";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    private Long brandId;

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    @BeforeEach
    void setUp() {
        AdminBrandV1Dto.CreateRequest brandRequest = new AdminBrandV1Dto.CreateRequest("나이키");
        ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> brandResponse = testRestTemplate.exchange(
            ADMIN_BRAND_ENDPOINT, HttpMethod.POST, new HttpEntity<>(brandRequest, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        brandId = brandResponse.getBody().data().id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long createProductViaAdmin(String name, int price, int stock) {
        AdminProductV1Dto.CreateRequest request = new AdminProductV1Dto.CreateRequest(brandId, name, price, stock);
        ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
            ADMIN_PRODUCT_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetAll {

        @DisplayName("상품이 존재하면, 페이지 결과를 반환한다.")
        @Test
        void returnsPageResult_whenProductsExist() {
            createProductViaAdmin("에어맥스", 129000, 100);
            createProductViaAdmin("에어포스1", 109000, 200);

            ResponseEntity<ApiResponse<ProductV1Dto.ProductPageResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=10", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().content()).hasSize(2),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2)
            );
        }

        @DisplayName("인증 없이도 조회할 수 있다.")
        @Test
        void returnsProducts_withoutAuth() {
            createProductViaAdmin("에어맥스", 129000, 100);

            ResponseEntity<ApiResponse<ProductV1Dto.ProductPageResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("브랜드별 필터링이 동작한다.")
        @Test
        void filtersByBrandId() {
            createProductViaAdmin("에어맥스", 129000, 100);

            ResponseEntity<ApiResponse<ProductV1Dto.ProductPageResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "?brandId=" + brandId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getBody().data().content()).hasSize(1),
                () -> assertThat(response.getBody().data().content().get(0).brandId()).isEqualTo(brandId)
            );
        }
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetById {

        @DisplayName("존재하는 상품이면, 상품 정보를 반환한다 (stock 미노출).")
        @Test
        void returnsProductInfo_whenProductExists() {
            Long productId = createProductViaAdmin("에어맥스", 129000, 100);

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + productId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(129000),
                () -> assertThat(response.getBody().data().likeCount()).isEqualTo(0)
            );
        }

        @DisplayName("존재하지 않는 상품이면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/999", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("인증 없이도 조회할 수 있다.")
        @Test
        void returnsProductInfo_withoutAuth() {
            Long productId = createProductViaAdmin("에어맥스", 129000, 100);

            ResponseEntity<ApiResponse<ProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + productId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertTrue(response.getStatusCode().is2xxSuccessful());
        }
    }
}
