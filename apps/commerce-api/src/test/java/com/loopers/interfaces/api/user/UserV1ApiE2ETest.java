package com.loopers.interfaces.api.user;

import com.loopers.domain.user.User;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api/v1/users";
    private static final String ENDPOINT_ME = "/api/v1/users/me";
    private static final String ENDPOINT_CHANGE_PASSWORD = "/api/v1/users/password";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private static final String VALID_LOGIN_ID = "testuser123";
    private static final String VALID_PASSWORD = "Password1!";
    private static final String VALID_NAME = "홍길동";
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(1990, 5, 15);
    private static final String VALID_EMAIL = "test@example.com";

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

    private User createTestUser() {
        String encodedPassword = passwordEncoder.encode(VALID_PASSWORD);
        User user = new User(VALID_LOGIN_ID, encodedPassword, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
        return userJpaRepository.save(user);
    }

    private HttpHeaders createAuthHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    @DisplayName("POST /api/v1/users (회원가입)")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 회원가입하면, 201 CREATED를 반환한다.")
        @Test
        void returnsCreated_whenValidRequestIsProvided() {
            // arrange
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // act
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(response.getBody().data().name()).isEqualTo(VALID_NAME)
            );
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면, 409 CONFLICT를 반환한다.")
        @Test
        void returnsConflict_whenLoginIdAlreadyExists() {
            // arrange
            createTestUser();
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                VALID_LOGIN_ID, VALID_PASSWORD, "다른이름", VALID_BIRTH_DATE, "other@example.com"
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // act
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("비밀번호가 8자 미만이면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenPasswordIsTooShort() {
            // arrange
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                VALID_LOGIN_ID, "Pass1!", VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // act
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/users/me (내 정보 조회)")
    @Nested
    class GetMe {

        @DisplayName("유효한 인증 정보로 조회하면, 마스킹된 이름이 반환된다.")
        @Test
        void returnsMaskedName_whenValidCredentials() {
            // arrange
            createTestUser();
            HttpHeaders headers = createAuthHeaders(VALID_LOGIN_ID, VALID_PASSWORD);

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*")
            );
        }

        @DisplayName("비밀번호가 틀리면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenPasswordIsWrong() {
            // arrange
            createTestUser();
            HttpHeaders headers = createAuthHeaders(VALID_LOGIN_ID, "WrongPassword1!");

            // act
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("PUT /api/v1/users/password (비밀번호 변경)")
    @Nested
    class ChangePassword {

        @DisplayName("유효한 요청이면, 200 OK를 반환한다.")
        @Test
        void returnsOk_whenValidRequest() {
            // arrange
            createTestUser();
            HttpHeaders headers = createAuthHeaders(VALID_LOGIN_ID, VALID_PASSWORD);
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(
                VALID_PASSWORD, "NewPassword1!"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PUT,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("현재 비밀번호가 틀리면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenCurrentPasswordIsWrong() {
            // arrange
            createTestUser();
            HttpHeaders headers = createAuthHeaders(VALID_LOGIN_ID, "WrongPassword1!");
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest(
                "WrongPassword1!", "NewPassword1!"
            );

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PUT,
                new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
