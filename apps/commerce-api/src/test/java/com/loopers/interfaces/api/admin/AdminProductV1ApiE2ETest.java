package com.loopers.interfaces.api.admin;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AdminAuthInterceptor;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;

import static com.loopers.interfaces.api.ApiResponse.Metadata.Result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 어드민 상품 API E2E. 모든 요청은 유효한 어드민 인증(LDAP+서명)이 필요하다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
class AdminProductV1ApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/products";
    private static final String LDAP_ID = "admin-product-e2e";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    /** 서버와 동일한 시크릿으로 서명해 유효한 어드민 헤더를 만들기 위함. 없으면 서명 불일치로 401. */
    @Value("${loopers.admin.auth.signing-secret:change-me-in-production}")
    private String signingSecret;

    private Long brandId;
    private Long createdProductId;

    @BeforeEach
    void setUp() {
        ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> brandRes = testRestTemplate.exchange(
            "/api-admin/v1/brands", HttpMethod.POST,
            new HttpEntity<>(new AdminBrandV1Dto.CreateBrandRequest("E2E상품테스트브랜드"), adminHeaders()),
            new ParameterizedTypeReference<>() {});
        ApiResponse<AdminBrandV1Dto.BrandResponse> brandBody = brandRes.getBody();
        assertThat(brandBody).isNotNull();
        brandId = brandBody.data().id();

        AdminProductV1Dto.CreateProductRequest body = new AdminProductV1Dto.CreateProductRequest(
            brandId, "E2E어드민상품", new BigDecimal("12000"), 7);
        ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> productRes = testRestTemplate.exchange(
            ENDPOINT, HttpMethod.POST, new HttpEntity<>(body, adminHeaders()),
            new ParameterizedTypeReference<>() {});
        ApiResponse<AdminProductV1Dto.ProductResponse> productBody = productRes.getBody();
        assertThat(productBody).isNotNull();
        createdProductId = productBody.data().id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        String signature = AdminAuthInterceptor.sign(LDAP_ID, signingSecret);
        HttpHeaders h = new HttpHeaders();
        h.set(AdminAuthInterceptor.HEADER_LDAP, LDAP_ID);
        h.set(AdminAuthInterceptor.HEADER_ADMIN_SIGNATURE, signature);
        return h;
    }

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    class CreateProduct {

        @Test
        @DisplayName("유효한 어드민 인증으로 상품 등록 시 201 및 생성된 상품 정보 반환")
        void withValidAuth_shouldReturn201() {
            AdminProductV1Dto.CreateProductRequest body = new AdminProductV1Dto.CreateProductRequest(
                brandId, "새상품", new BigDecimal("5000"), 3);

            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getBody()).isNotNull();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                () -> assertThat(response.getBody().data().name()).isEqualTo("새상품"),
                () -> assertThat(response.getBody().data().brandId()).isEqualTo(brandId),
                () -> assertThat(response.getBody().data().price()).isEqualByComparingTo(new BigDecimal("5000")),
                () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(3),
                () -> assertThat(response.getBody().data().id()).isNotNull()
            );
        }

        @Test
        @DisplayName("어드민 인증 없이 등록 시 401 반환")
        void withoutAuth_shouldReturn401() {
            AdminProductV1Dto.CreateProductRequest body = new AdminProductV1Dto.CreateProductRequest(
                brandId, "무단상품", new BigDecimal("1000"), 1);

            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(body), new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api-admin/v1/products/{productId}")
    @Nested
    class GetProduct {

        @Test
        @DisplayName("유효한 어드민 인증으로 조회 시 200 및 상품 정보 반환")
        void withValidAuth_shouldReturn200() {
            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + createdProductId, HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getBody()).isNotNull();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(createdProductId),
                () -> assertThat(response.getBody().data().name()).isEqualTo("E2E어드민상품"),
                () -> assertThat(response.getBody().data().price()).isEqualByComparingTo(new BigDecimal("12000")),
                () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(7)
            );
        }

        @Test
        @DisplayName("존재하지 않는 productId 조회 시 404 반환")
        void whenNotFound_shouldReturn404() {
            long nonExistent = 999_999L;

            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + nonExistent, HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("어드민 인증 없이 조회 시 401 반환")
        void withoutAuth_shouldReturn401() {
            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + createdProductId, HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId}")
    @Nested
    class UpdateProduct {

        @Test
        @DisplayName("유효한 어드민 인증으로 수정 시 200 및 변경된 상품 정보 반환")
        void withValidAuth_shouldReturn200() {
            AdminProductV1Dto.UpdateProductRequest body = new AdminProductV1Dto.UpdateProductRequest(
                "수정된상품명", new BigDecimal("15000"), 10);

            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + createdProductId, HttpMethod.PUT, new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getBody()).isNotNull();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("수정된상품명"),
                () -> assertThat(response.getBody().data().price()).isEqualByComparingTo(new BigDecimal("15000")),
                () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(10)
            );
        }

        @Test
        @DisplayName("어드민 인증 없이 수정 시 401 반환")
        void withoutAuth_shouldReturn401() {
            AdminProductV1Dto.UpdateProductRequest body = new AdminProductV1Dto.UpdateProductRequest(
                "무단수정", new BigDecimal("1"), 0);

            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + createdProductId, HttpMethod.PUT, new HttpEntity<>(body),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId}")
    @Nested
    class DeleteProduct {

        @Test
        @DisplayName("유효한 어드민 인증으로 삭제 시 204 반환")
        void withValidAuth_shouldReturn204() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + createdProductId, HttpMethod.DELETE, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("어드민 인증 없이 삭제 시 401 반환")
        void withoutAuth_shouldReturn401() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + createdProductId, HttpMethod.DELETE, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
