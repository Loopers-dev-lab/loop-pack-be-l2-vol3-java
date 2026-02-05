package com.loopers.interfaces.api;

import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.user.UserJpaRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api/v1/users";
    private static final String ENDPOINT_MY_INFO = "/api/v1/users/me";
    private static final String ENDPOINT_CHANGE_PASSWORD = "/api/v1/users/me/password";

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        PasswordEncoder passwordEncoder,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.passwordEncoder = passwordEncoder;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/users - 회원가입")
    @Nested
    class Register {

        @DisplayName("정상적인 정보가 주어지면, 회원가입이 성공한다.")
        @Test
        void registersUser_whenValidInfoIsProvided() {
            // arrange
            RegisterRequest request = new RegisterRequest(
                "testuser",
                "Test1234!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserResponse>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*")
            );
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면, CONFLICT 응답을 받는다.")
        @Test
        void returnsConflict_whenLoginIdAlreadyExists() {
            // arrange
            String encodedPassword = passwordEncoder.encode("Test1234!");
            userJpaRepository.save(
                UserModel.createWithEncodedPassword("existinguser", encodedPassword, "홍길동", LocalDate.of(1990, 1, 15), "existing@example.com")
            );

            RegisterRequest request = new RegisterRequest(
                "existinguser",
                "Test1234!",
                "김철수",
                LocalDate.of(1995, 5, 20),
                "new@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserResponse>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("GET /api/v1/users/me - 내 정보 조회")
    @Nested
    class GetMyInfo {

        @DisplayName("정상적인 인증 정보가 주어지면, 내 정보를 반환한다.")
        @Test
        void returnsMyInfo_whenValidCredentialsAreProvided() {
            // arrange
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            String encodedPassword = passwordEncoder.encode(rawPassword);
            userJpaRepository.save(
                UserModel.createWithEncodedPassword(loginId, encodedPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com")
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, rawPassword);

            // act
            ParameterizedTypeReference<ApiResponse<UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserResponse>> response = testRestTemplate.exchange(
                ENDPOINT_MY_INFO,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo(loginId),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("잘못된 비밀번호가 주어지면, UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenPasswordIsInvalid() {
            // arrange
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            String encodedPassword = passwordEncoder.encode(rawPassword);
            userJpaRepository.save(
                UserModel.createWithEncodedPassword(loginId, encodedPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com")
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, "WrongPass1!");

            // act
            ParameterizedTypeReference<ApiResponse<UserResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserResponse>> response = testRestTemplate.exchange(
                ENDPOINT_MY_INFO,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password - 비밀번호 수정")
    @Nested
    class ChangePassword {

        @DisplayName("정상적인 비밀번호가 주어지면, 비밀번호가 변경된다.")
        @Test
        void changesPassword_whenValidPasswordsAreProvided() {
            // arrange
            String loginId = "testuser";
            String currentPassword = "Test1234!";
            String encodedPassword = passwordEncoder.encode(currentPassword);
            userJpaRepository.save(
                UserModel.createWithEncodedPassword(loginId, encodedPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com")
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, currentPassword);

            ChangePasswordRequest request = new ChangePasswordRequest(currentPassword, "NewPass12!");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                new HttpEntity<>(request, headers),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("기존 비밀번호가 틀리면, UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenCurrentPasswordIsInvalid() {
            // arrange
            String loginId = "testuser";
            String currentPassword = "Test1234!";
            String encodedPassword = passwordEncoder.encode(currentPassword);
            userJpaRepository.save(
                UserModel.createWithEncodedPassword(loginId, encodedPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com")
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, currentPassword);

            ChangePasswordRequest request = new ChangePasswordRequest("WrongPass1!", "NewPass12!");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                new HttpEntity<>(request, headers),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 동일하면, BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            String loginId = "testuser";
            String currentPassword = "Test1234!";
            String encodedPassword = passwordEncoder.encode(currentPassword);
            userJpaRepository.save(
                UserModel.createWithEncodedPassword(loginId, encodedPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com")
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, currentPassword);

            ChangePasswordRequest request = new ChangePasswordRequest(currentPassword, currentPassword);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                new HttpEntity<>(request, headers),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    record RegisterRequest(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String email
    ) {}

    record ChangePasswordRequest(
        String currentPassword,
        String newPassword
    ) {}

    record UserResponse(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {}
}
