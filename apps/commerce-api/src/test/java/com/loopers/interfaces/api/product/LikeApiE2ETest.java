package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserDto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
@DisplayName("Like API E2E Tests")
class LikeApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final long ACTIVE_PRODUCT_ID = 1L;
    private static final long DELETED_PRODUCT_ID = 2L;
    private static final long NOT_FOUND_PRODUCT_ID = 9_999L;

    private static final String ENDPOINT_PRODUCTS = "/api/v1/products";
    private static final String ENDPOINT_LIKES = "/likes";
    private static final String ENDPOINT_ME_LIKES = "/api/v1/me/likes";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public LikeApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api/v1/products/{productId}/likes")
    class Register {

        @Test
        @DisplayName("인증된 사용자가 활성 상품에 좋아요를 누르면 201을 반환한다")
        void registerLike_whenActiveProductAndAuthenticatedUser_returnsCreated() {
            registerUser("likeApiUser", "Password1!", "홍길동", "19900101", "api-like@example.com", "010-1234-5678");

            HttpHeaders headers = headers("likeApiUser", "Password1!");

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    productLikesUrl(ACTIVE_PRODUCT_ID),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        @DisplayName("이미 좋아요한 상품을 다시 누르면 409을 반환한다")
        void registerLike_whenAlreadyLikedProduct_returnsConflict() {
            registerUser("likeApiConflictUser", "Password1!", "홍길동", "19900101", "api-like-conflict@example.com", "010-2345-6789");
            HttpHeaders headers = headers("likeApiConflictUser", "Password1!");

            testRestTemplate.exchange(
                    productLikesUrl(ACTIVE_PRODUCT_ID),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );
            ResponseEntity<ApiResponse<Void>> secondResponse = testRestTemplate.exchange(
                    productLikesUrl(ACTIVE_PRODUCT_ID),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("삭제된 상품에 대해 좋아요 요청을 보내면 400을 반환한다")
        void registerLike_whenDeletedProduct_returnsBadRequest() {
            registerUser("likeApiDeletedProductUser", "Password1!", "홍길동", "19900101", "api-like-deleted@example.com", "010-3456-7890");
            HttpHeaders headers = headers("likeApiDeletedProductUser", "Password1!");

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    productLikesUrl(DELETED_PRODUCT_ID),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("존재하지 않는 상품에 좋아요를 누르면 404를 반환한다")
        void registerLike_whenProductNotFound_returnsNotFound() {
            registerUser("likeApiMissingProductUser", "Password1!", "홍길동", "19900101", "api-like-missing@example.com", "010-4567-8901");
            HttpHeaders headers = headers("likeApiMissingProductUser", "Password1!");

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    productLikesUrl(NOT_FOUND_PRODUCT_ID),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("인증 정보가 없으면 401을 반환한다")
        void registerLike_whenNoAuthentication_returnsUnauthorized() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    productLikesUrl(ACTIVE_PRODUCT_ID),
                    HttpMethod.POST,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    class Cancel {

        @Test
        @DisplayName("좋아요 상태에서 삭제 요청하면 200을 반환한다")
        void cancelLike_whenLikedProduct_returnsOk() {
            registerUser("likeApiCancelUser", "Password1!", "홍길동", "19900101", "api-like-cancel@example.com", "010-6789-0123");
            HttpHeaders headers = headers("likeApiCancelUser", "Password1!");

            testRestTemplate.exchange(
                    productLikesUrl(ACTIVE_PRODUCT_ID),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    productLikesUrl(ACTIVE_PRODUCT_ID),
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("좋아요가 없는 상품 취소 요청은 404을 반환한다")
        void cancelLike_whenNotLikedProduct_returnsNotFound() {
            registerUser("likeApiCancelMissingUser", "Password1!", "홍길동", "19900101", "api-like-cancel-missing@example.com", "010-7890-1234");
            HttpHeaders headers = headers("likeApiCancelMissingUser", "Password1!");

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    productLikesUrl(ACTIVE_PRODUCT_ID),
                    HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("인증 정보가 없으면 401을 반환한다")
        void cancelLike_whenNoAuthentication_returnsUnauthorized() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    productLikesUrl(ACTIVE_PRODUCT_ID),
                    HttpMethod.DELETE,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/me/likes")
    class MyLikes {

        @Test
        @DisplayName("인증된 사용자가 기본 페이지/사이즈로 조회하면 200을 반환한다")
        void getMyLikes_whenAuthenticatedUserAndDefaultPagination_returnsOk() {
            registerUser("likeApiMeLikesUser", "Password1!", "홍길동", "19900101", "api-like-mylikes@example.com", "010-9012-3456");
            HttpHeaders headers = headers("likeApiMeLikesUser", "Password1!");

            testRestTemplate.exchange(
                    productLikesUrl(ACTIVE_PRODUCT_ID),
                    HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT_ME_LIKES,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("인증 정보가 없으면 401을 반환한다")
        void getMyLikes_whenNoAuthentication_returnsUnauthorized() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT_ME_LIKES,
                    HttpMethod.GET,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private HttpHeaders headers(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, password);
        return headers;
    }

    private String productLikesUrl(long productId) {
        return ENDPOINT_PRODUCTS + "/" + productId + ENDPOINT_LIKES;
    }

    private void registerUser(String loginId, String password, String name, String birthDate, String email, String phone) {
        UserDto.RegisterRequest request = new UserDto.RegisterRequest(
                loginId,
                password,
                name,
                birthDate,
                email,
                phone
        );
        testRestTemplate.exchange(
                "/api/v1/users",
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );
    }
}
