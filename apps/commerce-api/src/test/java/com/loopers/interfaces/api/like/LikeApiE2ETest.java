package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.brand.dto.BrandCreateRequest;
import com.loopers.interfaces.api.product.dto.ProductCreateRequest;
import com.loopers.interfaces.api.product.dto.ProductDetailResponse;
import com.loopers.interfaces.api.user.dto.UserRegisterRequest;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
class LikeApiE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final String LOGIN_ID = "e2euser1";
    private static final String PASSWORD = "Password1!";

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        registerUser(LOGIN_ID, PASSWORD, "홍길동");
        createBrand("나이키", "스포츠");
        createProduct(1L, "운동화", 50000, 100);
    }

    @Nested
    @DisplayName("E2E: 좋아요 전체 플로우")
    class LikeFlowE2E {

        @Test
        @DisplayName("좋아요 → 목록 조회 → 좋아요 취소 → 빈 목록 확인")
        void fullLikeFlow() {
            // Step 1: 좋아요
            HttpHeaders authHeaders = createAuthHeaders(LOGIN_ID, PASSWORD);
            ResponseEntity<Void> likeResponse = restTemplate.exchange(
                    "/api/v1/products/1/likes",
                    HttpMethod.POST,
                    new HttpEntity<>(authHeaders),
                    Void.class
            );
            assertThat(likeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Step 2: likeCount 확인
            ResponseEntity<ProductDetailResponse> productResponse = restTemplate.getForEntity(
                    "/api/v1/products/1",
                    ProductDetailResponse.class
            );
            assertThat(productResponse.getBody().likeCount()).isEqualTo(1);

            // Step 3: 좋아요 목록 조회
            ResponseEntity<String> likesResponse = restTemplate.exchange(
                    "/api/v1/users/" + LOGIN_ID + "/likes",
                    HttpMethod.GET,
                    new HttpEntity<>(authHeaders),
                    String.class
            );
            assertThat(likesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(likesResponse.getBody()).contains("운동화");

            // Step 4: 좋아요 취소
            ResponseEntity<Void> unlikeResponse = restTemplate.exchange(
                    "/api/v1/products/1/likes",
                    HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders),
                    Void.class
            );
            assertThat(unlikeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Step 5: likeCount 0 확인
            ResponseEntity<ProductDetailResponse> productAfter = restTemplate.getForEntity(
                    "/api/v1/products/1",
                    ProductDetailResponse.class
            );
            assertThat(productAfter.getBody().likeCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("좋아요 멱등성 - 중복 좋아요 시 likeCount 1 유지")
        void like_idempotent() {
            // given
            HttpHeaders authHeaders = createAuthHeaders(LOGIN_ID, PASSWORD);

            // when - 두 번 좋아요
            restTemplate.exchange("/api/v1/products/1/likes", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Void.class);
            restTemplate.exchange("/api/v1/products/1/likes", HttpMethod.POST,
                    new HttpEntity<>(authHeaders), Void.class);

            // then - likeCount는 1
            ResponseEntity<ProductDetailResponse> productResponse = restTemplate.getForEntity(
                    "/api/v1/products/1", ProductDetailResponse.class);
            assertThat(productResponse.getBody().likeCount()).isEqualTo(1);
        }
    }

    private HttpHeaders createAuthHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    private HttpHeaders createAdminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void registerUser(String loginId, String password, String name) {
        var request = new UserRegisterRequest(loginId, password, name,
                LocalDate.of(1990, 5, 15), "test@example.com");
        restTemplate.postForEntity("/api/v1/users", request, Void.class);
    }

    private void createBrand(String name, String description) {
        var request = new BrandCreateRequest(name, description);
        restTemplate.exchange("/api-admin/v1/brands", HttpMethod.POST,
                new HttpEntity<>(request, createAdminHeaders()), Void.class);
    }

    private void createProduct(Long brandId, String name, int price, int stock) {
        var request = new ProductCreateRequest(brandId, name, price, null, stock, "설명");
        restTemplate.exchange("/api-admin/v1/products", HttpMethod.POST,
                new HttpEntity<>(request, createAdminHeaders()), Void.class);
    }
}
