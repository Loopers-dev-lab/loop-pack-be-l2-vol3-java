package com.loopers.interfaces.api;

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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String SIGNUP_ENDPOINT = "/api/v1/users/signup";
    private static final String ME_ENDPOINT = "/api/v1/users/me";
    private static final String CHANGE_PASSWORD_ENDPOINT = "/api/v1/users/password";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String VALID_LOGIN_ID = "namjin123";
    private static final String VALID_PASSWORD = "qwer@1234";
    private static final String VALID_NAME = "namjin";
    private static final String EXPECTED_MASKED_NAME = "namji*";
    private static final String VALID_BIRTHDAY = "1994-05-25";
    private static final String VALID_EMAIL = "test@gmail.com";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/users/signup")
    @Nested
    class Signup {

        @DisplayName("정상적인 정보로 회원가입하면, 200 OK와 회원 정보를 반환한다.")
        @Test
        void returnsUserInfo_whenSignupIsValid() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(response.getBody().data().name()).isEqualTo(EXPECTED_MASKED_NAME),
                () -> assertThat(response.getBody().data().email()).isEqualTo(VALID_EMAIL)
            );
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenLoginIdAlreadyExists() {
            // arrange - 먼저 회원가입
            UserV1Dto.SignupRequest firstRequest = new UserV1Dto.SignupRequest(
                VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL
            );
            testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(firstRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {});

            // 같은 loginId로 다시 가입 시도
            UserV1Dto.SignupRequest duplicateRequest = new UserV1Dto.SignupRequest(
                VALID_LOGIN_ID, "other@1234", "다른사람", "1990-01-01", "other@gmail.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(duplicateRequest), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMe {

        @DisplayName("인증된 사용자가 내 정보를 조회하면, 200 OK와 회원 정보를 반환한다.")
        @Test
        void returnsUserInfo_whenAuthenticated() {
            // arrange - 회원가입
            UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
                VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL
            );
            testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(signupRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {});

            // 인증 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, VALID_LOGIN_ID);
            headers.set(HEADER_LOGIN_PW, VALID_PASSWORD);

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ME_ENDPOINT, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(response.getBody().data().name()).isEqualTo(EXPECTED_MASKED_NAME),
                () -> assertThat(response.getBody().data().email()).isEqualTo(VALID_EMAIL)
            );
        }

        @DisplayName("인증 헤더가 누락되면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenHeadersMissing() {
            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ME_ENDPOINT, HttpMethod.GET, null, responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("잘못된 비밀번호로 요청하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenPasswordIsWrong() {
            // arrange - 회원가입
            UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
                VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL
            );
            testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(signupRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {});

            // 잘못된 비밀번호로 인증 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, VALID_LOGIN_ID);
            headers.set(HEADER_LOGIN_PW, "wrongPw@123");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ME_ENDPOINT, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @DisplayName("PATCH /api/v1/users/password")
    @Nested
    class ChangePassword {

        private static final String NEW_PASSWORD = "newPw@1234";

        @DisplayName("정상적인 정보로 비밀번호를 변경하면, 200 OK를 반환한다.")
        @Test
        void returnsSuccess_whenChangePasswordIsValid() {
            // arrange - 회원가입
            UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
                VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL
            );
            testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(signupRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {});

            // 인증 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, VALID_LOGIN_ID);
            headers.set(HEADER_LOGIN_PW, VALID_PASSWORD);
            headers.set("Content-Type", "application/json");

            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(VALID_PASSWORD, NEW_PASSWORD);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(CHANGE_PASSWORD_ENDPOINT, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
            );
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenCurrentPasswordDoesNotMatch() {
            // arrange - 회원가입
            UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
                VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL
            );
            testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(signupRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {});

            // 인증 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, VALID_LOGIN_ID);
            headers.set(HEADER_LOGIN_PW, VALID_PASSWORD);
            headers.set("Content-Type", "application/json");

            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("wrongPw@123", NEW_PASSWORD);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(CHANGE_PASSWORD_ENDPOINT, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange - 회원가입
            UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
                VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL
            );
            testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(signupRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {});

            // 인증 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, VALID_LOGIN_ID);
            headers.set(HEADER_LOGIN_PW, VALID_PASSWORD);
            headers.set("Content-Type", "application/json");

            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(VALID_PASSWORD, VALID_PASSWORD);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(CHANGE_PASSWORD_ENDPOINT, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("인증 헤더가 누락되면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenHeadersMissing() {
            // arrange
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(VALID_PASSWORD, NEW_PASSWORD);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(CHANGE_PASSWORD_ENDPOINT, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("비밀번호 변경 후 새 비밀번호로 내 정보 조회가 성공한다.")
        @Test
        void canAccessMeWithNewPassword_afterPasswordChange() {
            // arrange - 회원가입
            UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
                VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL
            );
            testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(signupRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {});

            // 비밀번호 변경
            HttpHeaders changeHeaders = new HttpHeaders();
            changeHeaders.set(HEADER_LOGIN_ID, VALID_LOGIN_ID);
            changeHeaders.set(HEADER_LOGIN_PW, VALID_PASSWORD);
            changeHeaders.set("Content-Type", "application/json");

            UserV1Dto.ChangePasswordRequest changeRequest = new UserV1Dto.ChangePasswordRequest(VALID_PASSWORD, NEW_PASSWORD);
            testRestTemplate.exchange(CHANGE_PASSWORD_ENDPOINT, HttpMethod.PATCH, new HttpEntity<>(changeRequest, changeHeaders),
                new ParameterizedTypeReference<ApiResponse<Object>>() {});

            // 새 비밀번호로 내 정보 조회
            HttpHeaders meHeaders = new HttpHeaders();
            meHeaders.set(HEADER_LOGIN_ID, VALID_LOGIN_ID);
            meHeaders.set(HEADER_LOGIN_PW, NEW_PASSWORD);

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ME_ENDPOINT, HttpMethod.GET, new HttpEntity<>(meHeaders), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo(VALID_LOGIN_ID)
            );
        }
    }
}
