package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.brand.BrandAdminV1Dto;
import com.loopers.interfaces.api.product.ProductAdminV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
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
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LikeApiE2ETest {

    private static final String LIKE_ENDPOINT = "/api/v1/products/{productId}/likes";
    private static final String BRAND_ENDPOINT = "/api-admin/v1/brands";
    private static final String PRODUCT_ENDPOINT = "/api-admin/v1/products";
    private static final String USER_ENDPOINT = "/api/v1/users";
    private static final String VALID_LDAP = "admin-ldap";

    private static final String LOGIN_ID = "testuser";
    private static final String LOGIN_PW = "Test1234!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 좋아요_등록 {

        @Test
        void 활성_상품에_좋아요를_등록하면_200_응답() {
            signUpUser();
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            ResponseEntity<ApiResponse<Void>> response = postLike(productId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 좋아요_등록_시_해당_상품의_좋아요_수가_1_증가한다() {
            signUpUser();
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            ResponseEntity<ApiResponse<Void>> response = postLike(productId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> productResponse =
                    getProductList("?status=ACTIVE");
            assertThat(productResponse.getBody().data().content().get(0).likeCount()).isEqualTo(1);
        }

        @Test
        void 이미_좋아요한_상품에_재요청하면_200_응답하고_좋아요_수가_변동되지_않는다() {
            signUpUser();
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            postLike(productId);
            ResponseEntity<ApiResponse<Void>> response = postLike(productId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> {
                        ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> productResponse =
                                getProductList("?status=ACTIVE");
                        assertThat(productResponse.getBody().data().content().get(0).likeCount()).isEqualTo(1);
                    }
            );
        }

        @Test
        void 삭제된_상품에_좋아요_등록하면_404_응답() {
            signUpUser();
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            deleteProduct(productId);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    LIKE_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<>() {},
                    productId
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 상품입니다")
            );
        }

        @Test
        void 미존재_상품에_좋아요_등록하면_404_응답() {
            signUpUser();

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    LIKE_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<>() {},
                    999L
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 상품입니다")
            );
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    LIKE_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {},
                    1L
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증 헤더가 필요합니다")
            );
        }

        @Test
        void 인증에_실패하면_401_응답() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "notexist");
            headers.set("X-Loopers-LoginPw", "WrongPass1!");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    LIKE_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {},
                    1L
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증에 실패했습니다")
            );
        }
    }

    // --- 헬퍼 메서드 ---

    private void signUpUser() {
        UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                LOGIN_ID, LOGIN_PW, "홍길동",
                LocalDate.of(2000, 1, 15), "test@example.com"
        );
        testRestTemplate.exchange(
                USER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
        );
    }

    private Long registerBrand(String name, String description) {
        BrandAdminV1Dto.RegisterRequest request = new BrandAdminV1Dto.RegisterRequest(name, description);
        ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                BRAND_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    private Long registerProduct(Long brandId, String name, BigDecimal price, Integer stockQuantity, String description) {
        ProductAdminV1Dto.RegisterRequest request = new ProductAdminV1Dto.RegisterRequest(
                brandId, name, price, stockQuantity, description
        );
        ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                PRODUCT_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    private void deleteProduct(Long productId) {
        testRestTemplate.exchange(
                PRODUCT_ENDPOINT + "/" + productId, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );
    }

    private ResponseEntity<ApiResponse<Void>> postLike(Long productId) {
        return testRestTemplate.exchange(
                LIKE_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(userHeaders()),
                new ParameterizedTypeReference<>() {},
                productId
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> getProductList(String queryString) {
        return testRestTemplate.exchange(
                PRODUCT_ENDPOINT + queryString, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", LOGIN_PW);
        return headers;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", VALID_LDAP);
        return headers;
    }
}
