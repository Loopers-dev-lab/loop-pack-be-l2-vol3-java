package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.brand.admin.BrandAdminV1Dto;
import com.loopers.interfaces.api.member.MemberV1Dto;
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
import org.springframework.http.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public LikeV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }

    private HttpHeaders adminHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Loopers-Ldap", "loopers.admin");
        return h;
    }

    private Long createBrand(String name) {
        var req = new BrandAdminV1Dto.CreateRequest(name, "설명");
        return testRestTemplate.exchange("/api-admin/v1/brands", HttpMethod.POST, new HttpEntity<>(req, adminHeaders()),
            new ParameterizedTypeReference<ApiResponse<BrandAdminV1Dto.BrandResponse>>() {}).getBody().data().id();
    }

    private Long createProduct(String name, int price, Long brandId) {
        var req = new ProductAdminV1Dto.CreateRequest(name, "설명", price, brandId, 10);
        return testRestTemplate.exchange("/api-admin/v1/products", HttpMethod.POST, new HttpEntity<>(req, adminHeaders()),
            new ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>>() {}).getBody().data().id();
    }

    private void signupMember() {
        var req = new MemberV1Dto.SignupRequest("testuser", "Test1234!", "테스트유저",
            LocalDate.of(1998, 1, 1), "test@example.com");
        testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(req),
            new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {});
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Loopers-LoginId", "testuser");
        h.set("X-Loopers-LoginPw", "Test1234!");
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class LikeProduct {

        @DisplayName("상품에 좋아요를 등록한다")
        @Test
        void likesProduct() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId);
            signupMember();

            var response = testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/likes", HttpMethod.POST,
                new HttpEntity<>(null, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("같은 상품에 두 번 좋아요해도 200을 반환한다 (멱등성)")
        @Test
        void likeIsIdempotent() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId);
            signupMember();

            testRestTemplate.exchange("/api/v1/products/" + productId + "/likes",
                HttpMethod.POST, new HttpEntity<>(null, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

            var response = testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/likes", HttpMethod.POST,
                new HttpEntity<>(null, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("인증 없이 좋아요하면 400을 반환한다")
        @Test
        void returns400WhenNoAuth() {
            var response = testRestTemplate.exchange(
                "/api/v1/products/1/likes", HttpMethod.POST, null,
                new ParameterizedTypeReference<ApiResponse<Object>>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class UnlikeProduct {

        @DisplayName("좋아요를 취소한다")
        @Test
        void unlikesProduct() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId);
            signupMember();

            testRestTemplate.exchange("/api/v1/products/" + productId + "/likes",
                HttpMethod.POST, new HttpEntity<>(null, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

            var response = testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/likes", HttpMethod.DELETE,
                new HttpEntity<>(null, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("좋아요하지 않은 상품을 취소해도 200을 반환한다 (멱등성)")
        @Test
        void unlikeIsIdempotent() {
            Long brandId = createBrand("나이키");
            Long productId = createProduct("에어맥스", 129000, brandId);
            signupMember();

            var response = testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/likes", HttpMethod.DELETE,
                new HttpEntity<>(null, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

            assertTrue(response.getStatusCode().is2xxSuccessful());
        }
    }

    @DisplayName("GET /api/v1/users/{userId}/likes")
    @Nested
    class GetMyLikes {

        @DisplayName("내가 좋아요한 상품 목록을 조회한다")
        @Test
        void getsMyLikes() {
            Long brandId = createBrand("나이키");
            Long p1 = createProduct("에어맥스", 129000, brandId);
            Long p2 = createProduct("조던", 159000, brandId);
            signupMember();

            testRestTemplate.exchange("/api/v1/products/" + p1 + "/likes",
                HttpMethod.POST, new HttpEntity<>(null, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Void>>() {});
            testRestTemplate.exchange("/api/v1/products/" + p2 + "/likes",
                HttpMethod.POST, new HttpEntity<>(null, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

            var response = testRestTemplate.exchange(
                "/api/v1/users/1/likes", HttpMethod.GET,
                new HttpEntity<>(null, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Object>>() {});

            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("다른 유저의 좋아요 목록을 조회하면 400을 반환한다")
        @Test
        void returns400WhenUserIdMismatch() {
            signupMember();

            var response = testRestTemplate.exchange(
                "/api/v1/users/999/likes", HttpMethod.GET,
                new HttpEntity<>(null, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Object>>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
