package com.loopers.interfaces.api;

import com.loopers.domain.user.User;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api/v1/users";
    private static final String ENDPOINT_ME = "/api/v1/users/me";
    private static final String ENDPOINT_CHANGE_PASSWORD = "/api/v1/users/me/password";

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
    class Register {

        @DisplayName("유효한 정보로 회원가입하면, 201 CREATED와 userId를 반환한다.")
        @Test
        void returns201AndUserId_whenValidInfoIsProvided() {
            // arrange
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "testuser",
                "Test1234!",
                "홍길동",
                "19900101",
                "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().userId()).isNotNull()
            );
        }

        @DisplayName("이미 존재하는 loginId로 회원가입하면, 409 CONFLICT를 반환한다.")
        @Test
        void returns409Conflict_whenLoginIdAlreadyExists() {
            // arrange
            userJpaRepository.save(new User("testuser", "Test1234!", "홍길동", "19900101", "test@example.com"));
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "testuser",
                "Another12!",
                "김철수",
                "19950505",
                "another@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("유효하지 않은 입력으로 회원가입하면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returns400BadRequest_whenInputIsInvalid() {
            // arrange
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "test@user",  // 잘못된 loginId
                "Test1234!",
                "홍길동",
                "19900101",
                "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response =
                testRestTemplate.exchange(ENDPOINT_REGISTER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMe {

        @DisplayName("유효한 인증 정보로 조회하면, 200 OK와 마스킹된 사용자 정보를 반환한다.")
        @Test
        void returns200AndMaskedUserInfo_whenCredentialsAreValid() {
            // arrange
            userJpaRepository.save(new User("testuser", "Test1234!", "홍길동", "19900101", "test@example.com"));
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "Test1234!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MeResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍*동"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo("19900101"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("인증 헤더가 누락되면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returns400BadRequest_whenAuthHeaderIsMissing() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            // 헤더 없음

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MeResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 사용자로 조회하면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returns401Unauthorized_whenUserNotFound() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "nonexistent");
            headers.set("X-Loopers-LoginPw", "Test1234!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MeResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 틀리면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returns401Unauthorized_whenPasswordIsWrong() {
            // arrange
            userJpaRepository.save(new User("testuser", "Test1234!", "홍길동", "19900101", "test@example.com"));
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "WrongPass1!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MeResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MeResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password")
    @Nested
    class ChangePassword {

        @DisplayName("유효한 인증과 비밀번호로 변경하면, 200 OK와 성공 메시지를 반환한다.")
        @Test
        void returns200AndSuccessMessage_whenCredentialsAndPasswordAreValid() {
            // arrange
            userJpaRepository.save(new User("testuser", "Test1234!", "홍길동", "19900101", "test@example.com"));
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "Test1234!");
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("Test1234!", "NewPass12!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.ChangePasswordResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.ChangePasswordResponse>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().message()).isEqualTo("비밀번호가 성공적으로 변경되었습니다.")
            );
        }

        @DisplayName("헤더 비밀번호와 body currentPassword가 다르면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returns401Unauthorized_whenCurrentPasswordIsWrong() {
            // arrange
            userJpaRepository.save(new User("testuser", "Test1234!", "홍길동", "19900101", "test@example.com"));
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "Test1234!");
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("WrongPass1!", "NewPass12!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.ChangePasswordResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.ChangePasswordResponse>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("새 비밀번호가 규칙을 위반하면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returns400BadRequest_whenNewPasswordIsInvalid() {
            // arrange
            userJpaRepository.save(new User("testuser", "Test1234!", "홍길동", "19900101", "test@example.com"));
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "Test1234!");
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("Test1234!", "short");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.ChangePasswordResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.ChangePasswordResponse>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returns400BadRequest_whenNewPasswordIsSameAsCurrent() {
            // arrange
            userJpaRepository.save(new User("testuser", "Test1234!", "홍길동", "19900101", "test@example.com"));
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "Test1234!");
            UserV1Dto.ChangePasswordRequest request = new UserV1Dto.ChangePasswordRequest("Test1234!", "Test1234!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.ChangePasswordResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.ChangePasswordResponse>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH, new HttpEntity<>(request, headers), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
