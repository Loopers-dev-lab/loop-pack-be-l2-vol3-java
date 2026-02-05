package com.loopers.interfaces.api;

import com.loopers.interfaces.api.auth.AuthV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthV1ApiE2ETest {

    private static final String SIGNUP_URL = "/api/v1/auth/signup";
    private static final String CHANGE_PW_URL = "/api/v1/auth/password";
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

    // ========== 회원가입 ==========

    @DisplayName("POST /api/v1/auth/signup")
    @Nested
    class Signup {

        @DisplayName("유효한 정보로 가입하면, 201 Created 응답을 받는다.")
        @Test
        void returns201_whenValidRequest() {
            // act
            ResponseEntity<ApiResponse> response = signup(validSignupRequest());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @DisplayName("중복 로그인 ID로 가입하면, 409 Conflict 응답을 받는다.")
        @Test
        void returns409_whenDuplicateLoginId() {
            // arrange
            signup(validSignupRequest());

            // act
            ResponseEntity<ApiResponse> response = signup(validSignupRequest());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("잘못된 비밀번호 형식이면, 400 Bad Request 응답을 받는다.")
        @Test
        void returns400_whenInvalidPassword() {
            // arrange
            AuthV1Dto.SignupRequest request = new AuthV1Dto.SignupRequest(
                    "nahyeon", "short", "홍길동", "1994-11-15", "nahyeon@example.com"
            );

            // act
            ResponseEntity<ApiResponse> response = signup(request);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, 400 Bad Request 응답을 받는다.")
        @Test
        void returns400_whenPasswordContainsBirthDate() {
            // arrange
            AuthV1Dto.SignupRequest request = new AuthV1Dto.SignupRequest(
                    "nahyeon", "A19941115!", "홍길동", "1994-11-15", "nahyeon@example.com"
            );

            // act
            ResponseEntity<ApiResponse> response = signup(request);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ========== 비밀번호 변경 ==========

    @DisplayName("PATCH /api/v1/auth/password")
    @Nested
    class ChangePassword {

        @DisplayName("유효한 요청이면, 200 OK 응답을 받는다.")
        @Test
        void returns200_whenValidRequest() {
            // arrange
            signup(validSignupRequest());
            HttpHeaders headers = authHeaders("nahyeon", "Hx7!mK2@");
            headers.setContentType(MediaType.APPLICATION_JSON);
            AuthV1Dto.ChangePasswordRequest body = new AuthV1Dto.ChangePasswordRequest("Hx7!mK2@", "Nw8@pL3#");

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    CHANGE_PW_URL, HttpMethod.PUT, new HttpEntity<>(body, headers), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("변경 후 새 비밀번호로 인증되고, 이전 비밀번호로는 실패한다.")
        @Test
        void authenticatesWithNewPassword_afterChange() {
            // arrange - 회원가입 + 비밀번호 변경
            signup(validSignupRequest());
            HttpHeaders headers = authHeaders("nahyeon", "Hx7!mK2@");
            headers.setContentType(MediaType.APPLICATION_JSON);
            AuthV1Dto.ChangePasswordRequest body = new AuthV1Dto.ChangePasswordRequest("Hx7!mK2@", "Nw8@pL3#");
            testRestTemplate.exchange(CHANGE_PW_URL, HttpMethod.PUT, new HttpEntity<>(body, headers), ApiResponse.class);

            // act - 새 비밀번호로 조회
            HttpHeaders newHeaders = authHeaders("nahyeon", "Nw8@pL3#");
            ResponseEntity<ApiResponse> newPwResponse =
                    testRestTemplate.exchange(ME_URL, HttpMethod.GET, new HttpEntity<>(newHeaders), ApiResponse.class);

            // act - 이전 비밀번호로 조회
            HttpHeaders oldHeaders = authHeaders("nahyeon", "Hx7!mK2@");
            ResponseEntity<ApiResponse> oldPwResponse =
                    testRestTemplate.exchange(ME_URL, HttpMethod.GET, new HttpEntity<>(oldHeaders), ApiResponse.class);

            // assert
            assertAll(
                    () -> assertThat(newPwResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(oldPwResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }

        @DisplayName("현재 비밀번호와 동일한 새 비밀번호면, 400 Bad Request 응답을 받는다.")
        @Test
        void returns400_whenSamePassword() {
            // arrange
            signup(validSignupRequest());
            HttpHeaders headers = authHeaders("nahyeon", "Hx7!mK2@");
            headers.setContentType(MediaType.APPLICATION_JSON);
            AuthV1Dto.ChangePasswordRequest body = new AuthV1Dto.ChangePasswordRequest("Hx7!mK2@", "Hx7!mK2@");

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    CHANGE_PW_URL, HttpMethod.PUT, new HttpEntity<>(body, headers), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
