package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.brand.BrandAdminV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProductAdminApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/products";
    private static final String BRAND_ENDPOINT = "/api-admin/v1/brands";
    private static final String VALID_LDAP = "admin-ldap";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 상품_등록 {

        @Test
        void 유효한_정보로_등록하면_상품_정보가_반환된다() {
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            ProductAdminV1Dto.RegisterRequest request = new ProductAdminV1Dto.RegisterRequest(
                    brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화"
            );

            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = postRegister(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isNotNull(),
                    () -> assertThat(response.getBody().data().brandId()).isEqualTo(brandId),
                    () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키"),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("운동화"),
                    () -> assertThat(response.getBody().data().price()).isEqualByComparingTo(new BigDecimal("50000")),
                    () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(100),
                    () -> assertThat(response.getBody().data().description()).isEqualTo("편한 운동화"),
                    () -> assertThat(response.getBody().data().likeCount()).isEqualTo(0),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("ACTIVE"),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().updatedAt()).isNotNull()
            );
        }

        @Test
        void 미존재_브랜드면_404_응답() {
            ProductAdminV1Dto.RegisterRequest request = new ProductAdminV1Dto.RegisterRequest(
                    999L, "운동화", new BigDecimal("50000"), 100, "설명"
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 브랜드입니다")
            );
        }

        @Test
        void 삭제된_브랜드면_404_응답() {
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            deleteBrand(brandId);

            ProductAdminV1Dto.RegisterRequest request = new ProductAdminV1Dto.RegisterRequest(
                    brandId, "운동화", new BigDecimal("50000"), 100, "설명"
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 요청_필드_규칙_위반_시_400_응답() {
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            ProductAdminV1Dto.RegisterRequest request = new ProductAdminV1Dto.RegisterRequest(
                    brandId, "", new BigDecimal("50000"), 100, "설명"
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ProductAdminV1Dto.RegisterRequest request = new ProductAdminV1Dto.RegisterRequest(
                    1L, "운동화", new BigDecimal("50000"), 100, "설명"
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증 헤더가 필요합니다")
            );
        }

        @Test
        void 인증에_실패하면_401_응답() {
            ProductAdminV1Dto.RegisterRequest request = new ProductAdminV1Dto.RegisterRequest(
                    1L, "운동화", new BigDecimal("50000"), 100, "설명"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증에 실패했습니다")
            );
        }
    }

    // --- 헬퍼 메서드 ---

    private Long registerBrand(String name, String description) {
        BrandAdminV1Dto.RegisterRequest request = new BrandAdminV1Dto.RegisterRequest(name, description);
        ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                BRAND_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    private void deleteBrand(Long brandId) {
        testRestTemplate.exchange(
                BRAND_ENDPOINT + "/" + brandId, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );
    }

    private ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> postRegister(
            ProductAdminV1Dto.RegisterRequest request) {
        return testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", VALID_LDAP);
        return headers;
    }
}
