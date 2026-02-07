package com.loopers.interfaces.api;

import com.loopers.interfaces.api.user.UserRequest;
import com.loopers.interfaces.api.user.UserResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
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
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserApiE2ETest {

    private static final String USERS_URL = "/api/v1/users";
    private static final String ME_URL = "/api/v1/users/me";
    private static final String PASSWORD_URL = "/api/v1/users/password";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private UserRequest.SignupRequest validSignupRequest() {
        return new UserRequest.SignupRequest("nahyeon", "Hx7!mK2@", "홍길동", "1994-11-15", "nahyeon@example.com");
    }

    private ResponseEntity<ApiResponse> signup(UserRequest.SignupRequest request) {
        return testRestTemplate.postForEntity(USERS_URL, request, ApiResponse.class);
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    // ========== 회원가입 ==========

    @DisplayName("POST /api/v1/users")
    @Nested
    class Signup {

        @Test
        void 유효한_정보로_가입하면_201_Created_응답을_받는다() {
            // act
            ResponseEntity<ApiResponse> response = signup(validSignupRequest());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @Test
        void 중복_로그인_ID로_가입하면_409_Conflict_응답을_받는다() {
            // arrange
            signup(validSignupRequest());

            // act
            ResponseEntity<ApiResponse> response = signup(validSignupRequest());

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 잘못된_비밀번호_형식이면_400_Bad_Request_응답을_받는다() {
            // arrange
            UserRequest.SignupRequest request = new UserRequest.SignupRequest(
                    "nahyeon", "short", "홍길동", "1994-11-15", "nahyeon@example.com"
            );

            // act
            ResponseEntity<ApiResponse> response = signup(request);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 비밀번호에_생년월일이_포함되면_400_Bad_Request_응답을_받는다() {
            // arrange
            UserRequest.SignupRequest request = new UserRequest.SignupRequest(
                    "nahyeon", "A19941115!", "홍길동", "1994-11-15", "nahyeon@example.com"
            );

            // act
            ResponseEntity<ApiResponse> response = signup(request);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ========== 내 정보 조회 ==========

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMyInfo {

        @Test
        void 유효한_인증_정보로_조회하면_200_OK와_마스킹된_이름을_반환한다() {
            // arrange
            signup(validSignupRequest());
            HttpHeaders headers = authHeaders("nahyeon", "Hx7!mK2@");

            // act
            ParameterizedTypeReference<ApiResponse<UserResponse.UserDetailResponse>> type = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserResponse.UserDetailResponse>> response =
                    testRestTemplate.exchange(ME_URL, HttpMethod.GET, new HttpEntity<>(headers), type);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo("nahyeon")
            );
        }

        @Test
        void 잘못된_비밀번호로_조회하면_401_Unauthorized_응답을_받는다() {
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

    // ========== 비밀번호 변경 ==========

    @DisplayName("PUT /api/v1/users/password")
    @Nested
    class ChangePassword {

        @Test
        void 유효한_요청이면_200_OK_응답을_받는다() {
            // arrange
            signup(validSignupRequest());
            HttpHeaders headers = authHeaders("nahyeon", "Hx7!mK2@");
            headers.setContentType(MediaType.APPLICATION_JSON);
            UserRequest.ChangePasswordRequest body = new UserRequest.ChangePasswordRequest("Hx7!mK2@", "Nw8@pL3#");

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    PASSWORD_URL, HttpMethod.PUT, new HttpEntity<>(body, headers), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 변경_후_새_비밀번호로_인증되고_이전_비밀번호로는_실패한다() {
            // arrange
            signup(validSignupRequest());
            HttpHeaders headers = authHeaders("nahyeon", "Hx7!mK2@");
            headers.setContentType(MediaType.APPLICATION_JSON);
            UserRequest.ChangePasswordRequest body = new UserRequest.ChangePasswordRequest("Hx7!mK2@", "Nw8@pL3#");
            testRestTemplate.exchange(PASSWORD_URL, HttpMethod.PUT, new HttpEntity<>(body, headers), ApiResponse.class);

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

        @Test
        void 현재_비밀번호와_동일한_새_비밀번호면_400_Bad_Request_응답을_받는다() {
            // arrange
            signup(validSignupRequest());
            HttpHeaders headers = authHeaders("nahyeon", "Hx7!mK2@");
            headers.setContentType(MediaType.APPLICATION_JSON);
            UserRequest.ChangePasswordRequest body = new UserRequest.ChangePasswordRequest("Hx7!mK2@", "Hx7!mK2@");

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    PASSWORD_URL, HttpMethod.PUT, new HttpEntity<>(body, headers), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
