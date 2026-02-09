package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class UserApiE2ETest {

    private static final String ENDPOINT_USERS = "/api/v1/users";
    private static final String ENDPOINT_ME = "/api/v1/users/me";
    private static final String ENDPOINT_PASSWORD = "/api/v1/users/me/password";

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserApiE2ETest(
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

    @DisplayName("POST /api/v1/users - 회원가입")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 회원가입하면 201 Created를 반환한다")
        @Test
        void returnsCreated_whenValidRequest() {
            // arrange
            UserDto.RegisterRequest request = new UserDto.RegisterRequest(
                    "testuser1",
                    "Password1!",
                    "홍길동",
                    "19900101",
                    "test@example.com"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_USERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        @DisplayName("이미 존재하는 아이디로 가입하면 409 Conflict를 반환한다")
        @Test
        void returnsConflict_whenDuplicateUserId() {
            // arrange
            UserDto.RegisterRequest request = new UserDto.RegisterRequest(
                    "testuser1",
                    "Password1!",
                    "홍길동",
                    "19900101",
                    "test@example.com"
            );
            testRestTemplate.exchange(
                    ENDPOINT_USERS,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<ApiResponse<Void>>() {}
            );

            UserDto.RegisterRequest duplicateRequest = new UserDto.RegisterRequest(
                    "testuser1",
                    "Password2!",
                    "김철수",
                    "19950505",
                    "another@example.com"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_USERS,
                    HttpMethod.POST,
                    new HttpEntity<>(duplicateRequest),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("GET /api/v1/users/me - 내 정보 조회")
    @Nested
    class GetMe {

        @DisplayName("인증된 사용자가 조회하면 마스킹된 이름과 함께 정보를 반환한다")
        @Test
        void returnsUserInfo_whenAuthenticated() {
            // arrange
            String loginId = "testuser1";
            String password = "Password1!";
            registerUser(loginId, password, "홍길동", "19900101", "test@example.com");

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, password);

            // act
            ResponseEntity<ApiResponse<UserDto.UserResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ME,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser1"),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                    () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("인증 정보가 없으면 401 Unauthorized를 반환한다")
        @Test
        void returnsUnauthorized_whenNoCredentials() {
            // act
            ResponseEntity<ApiResponse<UserDto.UserResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ME,
                    HttpMethod.GET,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("잘못된 비밀번호로 조회하면 401 Unauthorized를 반환한다")
        @Test
        void returnsUnauthorized_whenWrongPassword() {
            // arrange
            String loginId = "testuser1";
            registerUser(loginId, "Password1!", "홍길동", "19900101", "test@example.com");

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, "WrongPass1!");

            // act
            ResponseEntity<ApiResponse<UserDto.UserResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_ME,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password - 비밀번호 변경")
    @Nested
    class ChangePassword {

        @DisplayName("유효한 요청으로 비밀번호를 변경하면 200 OK를 반환한다")
        @Test
        void returnsOk_whenValidRequest() {
            // arrange
            String loginId = "testuser1";
            String currentPassword = "Password1!";
            registerUser(loginId, currentPassword, "홍길동", "19900101", "test@example.com");

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, currentPassword);

            UserDto.ChangePasswordRequest request = new UserDto.ChangePasswordRequest(
                    currentPassword,
                    "NewPassword1!"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_PASSWORD,
                    HttpMethod.PATCH,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("현재 비밀번호가 틀리면 401 Unauthorized를 반환한다")
        @Test
        void returnsUnauthorized_whenCurrentPasswordWrong() {
            // arrange
            String loginId = "testuser1";
            registerUser(loginId, "Password1!", "홍길동", "19900101", "test@example.com");

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, "Password1!");

            UserDto.ChangePasswordRequest request = new UserDto.ChangePasswordRequest(
                    "WrongPassword1!",
                    "NewPassword1!"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_PASSWORD,
                    HttpMethod.PATCH,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 400 Bad Request를 반환한다")
        @Test
        void returnsBadRequest_whenSamePassword() {
            // arrange
            String loginId = "testuser1";
            String currentPassword = "Password1!";
            registerUser(loginId, currentPassword, "홍길동", "19900101", "test@example.com");

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, currentPassword);

            UserDto.ChangePasswordRequest request = new UserDto.ChangePasswordRequest(
                    currentPassword,
                    currentPassword
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT_PASSWORD,
                    HttpMethod.PATCH,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private void registerUser(String loginId, String password, String name, String birthDate, String email) {
        UserDto.RegisterRequest request = new UserDto.RegisterRequest(loginId, password, name, birthDate, email);
        testRestTemplate.exchange(
                ENDPOINT_USERS,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );
    }
}
