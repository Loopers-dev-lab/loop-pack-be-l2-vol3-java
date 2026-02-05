package com.loopers.interfaces.api;

import com.loopers.interfaces.api.auth.AuthV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String SIGNUP_URL = "/api/v1/auth/signup";
    private static final String ME_URL = "/api/v1/users/me";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private AuthV1Dto.SignupRequest validSignupRequest() {
        return new AuthV1Dto.SignupRequest("nahyeon", "Hx7!mK2@", "홍길동", "1994-11-15", "nahyeon@example.com");
    }

    private ResponseEntity<ApiResponse> signup(AuthV1Dto.SignupRequest request) {
        return testRestTemplate.postForEntity(SIGNUP_URL, request, ApiResponse.class);
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    // ========== 내 정보 조회 ==========

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMyInfo {

        @DisplayName("유효한 인증 정보로 조회하면, 200 OK + 마스킹된 이름을 반환한다.")
        @Test
        void returns200WithMaskedName_whenValidAuth() {
            // arrange
            signup(validSignupRequest());
            HttpHeaders headers = authHeaders("nahyeon", "Hx7!mK2@");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                    testRestTemplate.exchange(ME_URL, HttpMethod.GET, new HttpEntity<>(headers), type);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo("nahyeon")
            );
        }

        @DisplayName("잘못된 비밀번호로 조회하면, 401 Unauthorized 응답을 받는다.")
        @Test
        void returns401_whenWrongPassword() {
            // arrange
            signup(validSignupRequest());
            HttpHeaders headers = authHeaders("nahyeon", "wrongPw1!");

            // act
            ResponseEntity<ApiResponse> response =
                    testRestTemplate.exchange(ME_URL, HttpMethod.GET, new HttpEntity<>(headers), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}
