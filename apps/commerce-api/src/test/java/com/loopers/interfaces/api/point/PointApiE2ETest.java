package com.loopers.interfaces.api.point;

import com.loopers.domain.point.PointAccount;
import com.loopers.domain.point.PointAccountRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserRequest;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PointApiE2ETest {

    @Autowired private TestRestTemplate testRestTemplate;
    @Autowired private PointAccountRepository pointAccountRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    private Long userId;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @BeforeEach
    void setUp() {
        UserRequest.SignupRequest signupRequest = new UserRequest.SignupRequest(
                "testuser", "Hx7!mK2@", "테스터", "1994-11-15", "test@example.com");
        testRestTemplate.postForEntity("/api/v1/users", signupRequest, ApiResponse.class);
        userId = 1L;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "testuser");
        headers.set("X-Loopers-LoginPw", "Hx7!mK2@");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @DisplayName("GET /api/v1/users/me/points")
    @Nested
    class 포인트_잔액_조회 {

        @Test
        void 조회에_성공하면_200_OK를_반환한다() {
            // arrange
            pointAccountRepository.save(PointAccount.create(userId));

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/me/points", HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 계정이_없으면_404_Not_Found를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/me/points", HttpMethod.GET,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 인증_없이_요청하면_401_Unauthorized를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/users/me/points", HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
