package com.loopers.interfaces.api.auth;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.like.LikeV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.support.auth.CustomerAuthInterceptor;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 고객 API 인증 E2E.
 * 공백 헤더·존재하지 않는 loginId → 401, 유효한 사용자 → 통과.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
class CustomerAuthE2ETest {

    private static final String ENDPOINT_LIKES = "/api/v1/likes";
    private static final String VALID_LOGIN_ID = "authuser";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        UserV1Dto.SignUpRequest signUp = new UserV1Dto.SignUpRequest(
                VALID_LOGIN_ID, "SecurePass1!", "auth@example.com", "1990-01-15", "MALE");
        testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(signUp),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {
                });
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders loginIdHeader(String loginId) {
        HttpHeaders h = new HttpHeaders();
        h.set(CustomerAuthInterceptor.HEADER_LOGIN_ID, loginId);
        return h;
    }

    @DisplayName("고객 API 인증 실패 케이스")
    @Nested
    class Unauthorized {

        @Test
        @DisplayName("헤더 없이 요청 시 401 반환")
        void request_withNoHeader_shouldReturn401() {
            ResponseEntity<ApiResponse<LikeV1Dto.PagedLikesResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_LIKES + "?page=0&size=20", HttpMethod.GET, new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {
                    });

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("공백 헤더로 요청 시 401 반환")
        void request_withBlankHeader_shouldReturn401() {
            HttpHeaders headers = loginIdHeader("   ");

            ResponseEntity<ApiResponse<LikeV1Dto.PagedLikesResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_LIKES + "?page=0&size=20", HttpMethod.GET, new HttpEntity<>(null, headers),
                    new ParameterizedTypeReference<>() {
                    });

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("존재하지 않는 loginId로 요청 시 401 반환")
        void request_withNonExistentLoginId_shouldReturn401() {
            HttpHeaders headers = loginIdHeader("nonexistent-user-id");

            ResponseEntity<ApiResponse<LikeV1Dto.PagedLikesResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_LIKES + "?page=0&size=20", HttpMethod.GET, new HttpEntity<>(null, headers),
                    new ParameterizedTypeReference<>() {
                    });

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("고객 API 인증 성공 케이스")
    @Nested
    class Authorized {

        @Test
        @DisplayName("유효한 loginId로 요청 시 통과")
        void request_withValidLoginId_shouldPassThrough() {
            ResponseEntity<ApiResponse<LikeV1Dto.PagedLikesResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_LIKES + "?page=0&size=20", HttpMethod.GET, new HttpEntity<>(loginIdHeader(VALID_LOGIN_ID)),
                    new ParameterizedTypeReference<>() {
                    });

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            ApiResponse<LikeV1Dto.PagedLikesResponse> body = response.getBody();
            assertThat(body).isNotNull();
            assertThat(body.data().content()).isEmpty();
        }
    }
}
