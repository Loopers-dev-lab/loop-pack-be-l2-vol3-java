package com.loopers.interfaces.api;

import com.loopers.interfaces.api.brand.AdminBrandV1Dto;
import com.loopers.interfaces.api.like.LikeV1Dto;
import com.loopers.interfaces.api.product.AdminProductV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private static final String LIKE_ENDPOINT = "/api/v1/products/{productId}/likes";
    private static final String MY_LIKES_ENDPOINT = "/api/v1/likes";
    private static final String ADMIN_BRAND_ENDPOINT = "/api-admin/v1/brands";
    private static final String ADMIN_PRODUCT_ENDPOINT = "/api-admin/v1/products";
    private static final String SIGNUP_ENDPOINT = "/api/v1/users";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public LikeV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    private Long productId;

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "testUser1");
        headers.set("X-Loopers-LoginPw", "Abcd1234!");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private void signupUser() {
        UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
            "testUser1", "Abcd1234!", "홍길동", LocalDate.of(1995, 3, 15), "test@example.com"
        );
        testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request),
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {});
    }

    @BeforeEach
    void setUp() {
        signupUser();

        AdminBrandV1Dto.CreateRequest brandRequest = new AdminBrandV1Dto.CreateRequest("나이키");
        ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> brandResponse = testRestTemplate.exchange(
            ADMIN_BRAND_ENDPOINT, HttpMethod.POST, new HttpEntity<>(brandRequest, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        Long brandId = brandResponse.getBody().data().id();

        AdminProductV1Dto.CreateRequest productRequest = new AdminProductV1Dto.CreateRequest(brandId, "에어맥스", 129000, 100);
        ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> productResponse = testRestTemplate.exchange(
            ADMIN_PRODUCT_ENDPOINT, HttpMethod.POST, new HttpEntity<>(productRequest, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        productId = productResponse.getBody().data().id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private String likeUrl(Long productId) {
        return "/api/v1/products/" + productId + "/likes";
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class LikeProduct {

        @DisplayName("인증된 사용자가 좋아요하면, 200 OK를 반환한다.")
        @Test
        void returnsSuccess_whenAuthenticated() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                likeUrl(productId), HttpMethod.POST, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("이미 좋아요한 상품이면, 409 CONFLICT를 반환한다.")
        @Test
        void returnsConflict_whenAlreadyLiked() {
            testRestTemplate.exchange(
                likeUrl(productId), HttpMethod.POST, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                likeUrl(productId), HttpMethod.POST, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("존재하지 않는 상품이면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                likeUrl(999L), HttpMethod.POST, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("인증되지 않은 사용자이면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenNotAuthenticated() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                likeUrl(productId), HttpMethod.POST, null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class UnlikeProduct {

        @DisplayName("좋아요가 존재하면, 200 OK를 반환한다.")
        @Test
        void returnsSuccess_whenLikeExists() {
            testRestTemplate.exchange(
                likeUrl(productId), HttpMethod.POST, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                likeUrl(productId), HttpMethod.DELETE, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("좋아요가 존재하지 않으면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenLikeDoesNotExist() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                likeUrl(productId), HttpMethod.DELETE, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/likes")
    @Nested
    class GetMyLikes {

        @DisplayName("좋아요한 상품이 있으면, 목록을 반환한다.")
        @Test
        void returnsLikes_whenLikesExist() {
            testRestTemplate.exchange(
                likeUrl(productId), HttpMethod.POST, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );

            ResponseEntity<ApiResponse<LikeV1Dto.LikeListResponse>> response = testRestTemplate.exchange(
                MY_LIKES_ENDPOINT, HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().likes()).hasSize(1),
                () -> assertThat(response.getBody().data().likes().get(0).productName()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().likes().get(0).brandName()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().likes().get(0).price()).isEqualTo(129000)
            );
        }

        @DisplayName("좋아요한 상품이 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoLikes() {
            ResponseEntity<ApiResponse<LikeV1Dto.LikeListResponse>> response = testRestTemplate.exchange(
                MY_LIKES_ENDPOINT, HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().likes()).isEmpty()
            );
        }

        @DisplayName("인증되지 않은 사용자이면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenNotAuthenticated() {
            ResponseEntity<ApiResponse<LikeV1Dto.LikeListResponse>> response = testRestTemplate.exchange(
                MY_LIKES_ENDPOINT, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
