package com.loopers.interfaces.api;

import com.loopers.infrastructure.user.UserJpaRepository;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_SIGNUP = "/api/v1/users";
    private static final String ENDPOINT_ME = "/api/v1/users/me";
    private static final String ENDPOINT_CHANGE_PASSWORD = "/api/v1/users/password";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/users")
    @Nested
    class Signup {

        @DisplayName("올바른 요청이면, 200 OK와 함께 유저 정보를 반환한다.")
        @Test
        void returnsUserInfo_whenValidRequest() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "testUser1", "Abcd1234!", "홍길동", LocalDate.of(1995, 3, 15), "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("testUser1"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길동"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(1995, 3, 15))
            );
        }

        @DisplayName("이미 존재하는 로그인 ID로 요청하면, 409 CONFLICT 응답을 받는다.")
        @Test
        void returnsConflict_whenLoginIdAlreadyExists() {
            // arrange
            UserV1Dto.SignupRequest firstRequest = new UserV1Dto.SignupRequest(
                "testUser1", "Abcd1234!", "홍길동", LocalDate.of(1995, 3, 15), "test@example.com"
            );
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(firstRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {});

            UserV1Dto.SignupRequest duplicateRequest = new UserV1Dto.SignupRequest(
                "testUser1", "Abcd5678!", "김철수", LocalDate.of(2000, 1, 1), "other@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(duplicateRequest), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );
        }

        @DisplayName("잘못된 비밀번호 형식이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenPasswordIsInvalid() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "testUser1", "short", "홍길동", LocalDate.of(1995, 3, 15), "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }

        @DisplayName("잘못된 이메일 형식이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenEmailIsInvalid() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "testUser1", "Abcd1234!", "홍길동", LocalDate.of(1995, 3, 15), "invalid-email"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }

        @DisplayName("로그인 ID에 특수문자가 포함되면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenLoginIdContainsSpecialChars() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "user@123", "Abcd1234!", "홍길동", LocalDate.of(1995, 3, 15), "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }
    }

    @DisplayName("PUT /api/v1/users/password")
    @Nested
    class ChangePassword {

        private void signupUser() {
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "testUser1", "Abcd1234!", "홍길동", LocalDate.of(1995, 3, 15), "test@example.com"
            );
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {});
        }

        @DisplayName("올바른 요청이면, 200 OK를 반환한다.")
        @Test
        void returnsSuccess_whenValidRequest() {
            // arrange
            signupUser();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testUser1");
            headers.set("X-Loopers-LoginPw", "Abcd1234!");
            headers.set("Content-Type", "application/json");

            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("Abcd1234!", "NewPass123!");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PUT, new HttpEntity<>(request, headers), responseType);

            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("기존 비밀번호가 틀리면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenCurrentPasswordIsWrong() {
            // arrange
            signupUser();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testUser1");
            headers.set("X-Loopers-LoginPw", "Abcd1234!");
            headers.set("Content-Type", "application/json");

            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("WrongPass1!", "NewPass123!");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PUT, new HttpEntity<>(request, headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 같으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            signupUser();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testUser1");
            headers.set("X-Loopers-LoginPw", "Abcd1234!");
            headers.set("Content-Type", "application/json");

            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("Abcd1234!", "Abcd1234!");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PUT, new HttpEntity<>(request, headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }

        @DisplayName("새 비밀번호가 규칙에 위반되면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNewPasswordViolatesPolicy() {
            // arrange
            signupUser();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testUser1");
            headers.set("X-Loopers-LoginPw", "Abcd1234!");
            headers.set("Content-Type", "application/json");

            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("Abcd1234!", "short");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PUT, new HttpEntity<>(request, headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }

        @DisplayName("인증되지 않은 사용자이면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenNotAuthenticated() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "nonExistent");
            headers.set("X-Loopers-LoginPw", "Abcd1234!");
            headers.set("Content-Type", "application/json");

            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("Abcd1234!", "NewPass123!");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PUT, new HttpEntity<>(request, headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMe {

        private void signupUser() {
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "testUser1", "Abcd1234!", "홍길동", LocalDate.of(1995, 3, 15), "test@example.com"
            );
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {});
        }

        @DisplayName("올바른 인증 헤더이면, 200 OK와 함께 마스킹된 이름이 포함된 유저 정보를 반환한다.")
        @Test
        void returnsUserInfoWithMaskedName_whenValidAuthHeaders() {
            // arrange
            signupUser();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testUser1");
            headers.set("X-Loopers-LoginPw", "Abcd1234!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MeResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("testUser1"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(1995, 3, 15))
            );
        }

        @DisplayName("존재하지 않는 로그인 ID이면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenLoginIdNotFound() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "nonExistent");
            headers.set("X-Loopers-LoginPw", "Abcd1234!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MeResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }

        @DisplayName("비밀번호가 틀리면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenPasswordIsWrong() {
            // arrange
            signupUser();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testUser1");
            headers.set("X-Loopers-LoginPw", "WrongPass1!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MeResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }
    }
}
