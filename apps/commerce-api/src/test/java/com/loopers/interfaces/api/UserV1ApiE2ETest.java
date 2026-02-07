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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api/v1/users/register";
    private static final String ENDPOINT_ME = "/api/v1/users/me";
    private static final String ENDPOINT_CHANGE_PASSWORD = "/api/v1/users/me/password";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/users/register - 회원가입")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 요청하면, 회원이 생성되고 회원 정보를 반환한다")
        @Test
        void returnsCreatedUser_whenValidRequest() {
            // arrange
            var request = new UserV1Dto.RegisterRequest(
                "testuser1",
                "Test1234!",
                "홍길동",
                "test@example.com",
                "1990-01-15"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser1"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길동"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo("1990-01-15")
            );
        }

        @DisplayName("이미 존재하는 loginId로 요청하면, CONFLICT 응답을 반환한다")
        @Test
        void returnsConflict_whenLoginIdAlreadyExists() {
            // arrange
            var request = new UserV1Dto.RegisterRequest(
                "testuser1",
                "Test1234!",
                "홍길동",
                "test@example.com",
                "1990-01-15"
            );
            testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {});

            var duplicateRequest = new UserV1Dto.RegisterRequest(
                "testuser1",
                "Other1234!",
                "김철수",
                "other@example.com",
                "1985-05-20"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(duplicateRequest), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("잘못된 loginId 형식으로 요청하면, BAD_REQUEST 응답을 반환한다")
        @Test
        void returnsBadRequest_whenInvalidLoginId() {
            // arrange
            var request = new UserV1Dto.RegisterRequest(
                "invalid_id!",
                "Test1234!",
                "홍길동",
                "test@example.com",
                "1990-01-15"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("잘못된 비밀번호 형식으로 요청하면, BAD_REQUEST 응답을 반환한다")
        @Test
        void returnsBadRequest_whenInvalidPassword() {
            // arrange
            var request = new UserV1Dto.RegisterRequest(
                "testuser1",
                "short",
                "홍길동",
                "test@example.com",
                "1990-01-15"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, BAD_REQUEST 응답을 반환한다")
        @Test
        void returnsBadRequest_whenPasswordContainsBirthDate() {
            // arrange
            var request = new UserV1Dto.RegisterRequest(
                "testuser1",
                "Test19900115!",
                "홍길동",
                "test@example.com",
                "1990-01-15"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("잘못된 이메일 형식으로 요청하면, BAD_REQUEST 응답을 반환한다")
        @Test
        void returnsBadRequest_whenInvalidEmail() {
            // arrange
            var request = new UserV1Dto.RegisterRequest(
                "testuser1",
                "Test1234!",
                "홍길동",
                "invalid-email",
                "1990-01-15"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("잘못된 생년월일 형식으로 요청하면, BAD_REQUEST 응답을 반환한다")
        @Test
        void returnsBadRequest_whenInvalidBirthDate() {
            // arrange
            var request = new UserV1Dto.RegisterRequest(
                "testuser1",
                "Test1234!",
                "홍길동",
                "test@example.com",
                "19900115"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/users/me - 내 정보 조회")
    @Nested
    class GetMe {

        @DisplayName("유효한 인증 정보로 요청하면, 내 정보를 반환한다 (이름 마스킹 포함)")
        @Test
        void returnsMyInfo_whenValidCredentials() {
            // arrange
            String loginId = "testuser1";
            String password = "Test1234!";
            registerUser(loginId, password, "홍길동", "test@example.com", "1990-01-15");

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, password);

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MeResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser1"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo("1990-01-15")
            );
        }

        @DisplayName("잘못된 비밀번호로 요청하면, UNAUTHORIZED 응답을 반환한다")
        @Test
        void returnsUnauthorized_whenInvalidPassword() {
            // arrange
            String loginId = "testuser1";
            registerUser(loginId, "Test1234!", "홍길동", "test@example.com", "1990-01-15");

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, "WrongPassword1!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MeResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 loginId로 요청하면, UNAUTHORIZED 응답을 반환한다")
        @Test
        void returnsUnauthorized_whenLoginIdNotFound() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, "nonexistent");
            headers.set(HEADER_LOGIN_PW, "Test1234!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MeResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("X-Loopers-LoginId 헤더가 누락되면, BAD_REQUEST 응답을 반환한다")
        @Test
        void returnsBadRequest_whenLoginIdHeaderMissing() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_PW, "Test1234!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MeResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("X-Loopers-LoginPw 헤더가 누락되면, BAD_REQUEST 응답을 반환한다")
        @Test
        void returnsBadRequest_whenLoginPwHeaderMissing() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, "testuser1");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MeResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        private void registerUser(String loginId, String password, String name, String email, String birthDate) {
            var request = new UserV1Dto.RegisterRequest(loginId, password, name, email, birthDate);
            testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {});
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password - 비밀번호 변경")
    @Nested
    class ChangePassword {

        @DisplayName("유효한 요청으로 비밀번호를 변경하면, 200 OK를 반환한다")
        @Test
        void returnsOk_whenValidRequest() {
            // arrange
            String loginId = "testuser1";
            String currentPassword = "Test1234!";
            String newPassword = "NewPass1234!";
            registerUser(loginId, currentPassword, "홍길동", "test@example.com", "1990-01-15");

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, currentPassword);

            var request = new UserV1Dto.ChangePasswordRequest(currentPassword, newPassword);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("비밀번호 변경 후 이전 비밀번호로 인증하면 401, 새 비밀번호로 인증하면 200을 반환한다")
        @Test
        void returnsUnauthorizedWithOldPassword_andOkWithNewPassword_afterChange() {
            // arrange
            String loginId = "testuser1";
            String currentPassword = "Test1234!";
            String newPassword = "NewPass1234!";
            registerUser(loginId, currentPassword, "홍길동", "test@example.com", "1990-01-15");

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, currentPassword);

            var request = new UserV1Dto.ChangePasswordRequest(currentPassword, newPassword);
            testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<ApiResponse<Void>>() {});

            // act - 이전 비밀번호로 인증 시도
            HttpHeaders oldPasswordHeaders = new HttpHeaders();
            oldPasswordHeaders.set(HEADER_LOGIN_ID, loginId);
            oldPasswordHeaders.set(HEADER_LOGIN_PW, currentPassword);

            ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>> meResponseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MeResponse>> oldPasswordResponse =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(oldPasswordHeaders), meResponseType);

            // act - 새 비밀번호로 인증 시도
            HttpHeaders newPasswordHeaders = new HttpHeaders();
            newPasswordHeaders.set(HEADER_LOGIN_ID, loginId);
            newPasswordHeaders.set(HEADER_LOGIN_PW, newPassword);

            ResponseEntity<ApiResponse<UserV1Dto.MeResponse>> newPasswordResponse =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(newPasswordHeaders), meResponseType);

            // assert
            assertAll(
                () -> assertThat(oldPasswordResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(newPasswordResponse.getStatusCode()).isEqualTo(HttpStatus.OK)
            );
        }

        @DisplayName("currentPassword가 실제 비밀번호와 불일치하면, 401 UNAUTHORIZED를 반환한다")
        @Test
        void returnsUnauthorized_whenCurrentPasswordMismatch() {
            // arrange
            String loginId = "testuser1";
            String currentPassword = "Test1234!";
            registerUser(loginId, currentPassword, "홍길동", "test@example.com", "1990-01-15");

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, currentPassword);

            var request = new UserV1Dto.ChangePasswordRequest("WrongPassword1!", "NewPass1234!");

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("newPassword가 currentPassword와 같으면, 400 BAD_REQUEST를 반환한다")
        @Test
        void returnsBadRequest_whenNewPasswordSameAsCurrent() {
            // arrange
            String loginId = "testuser1";
            String currentPassword = "Test1234!";
            registerUser(loginId, currentPassword, "홍길동", "test@example.com", "1990-01-15");

            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, currentPassword);

            var request = new UserV1Dto.ChangePasswordRequest(currentPassword, currentPassword);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        private void registerUser(String loginId, String password, String name, String email, String birthDate) {
            var request = new UserV1Dto.RegisterRequest(loginId, password, name, email, birthDate);
            testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {});
        }
    }
}
