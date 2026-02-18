package com.loopers.interfaces.api;

import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.UserJpaRepository;
import com.loopers.interfaces.user.ChangePasswordRequest;
import com.loopers.interfaces.user.UserDto;
import com.loopers.interfaces.user.UsersSignUpRequestDto;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UsersApiE2ETest {

    private static final String ENDPOINT_USERS = "/users";
    private static final String ENDPOINT_USERS_ME = "/users/me";
    private static final String ENDPOINT_USERS_ME_PASSWORD = "/users/me/password";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UsersApiE2ETest(
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

    private HttpHeaders createHeaders(String loginId, String loginPw) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(LoopersHeaders.X_LOOPERS_LOGIN_ID, loginId);
        headers.set(LoopersHeaders.X_LOOPERS_LOGIN_PW, loginPw);
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private UserModel createUser(String loginId, String rawPassword, LocalDate birthDate, String name, String email) {
        String encodedPassword = passwordEncoder.encode(rawPassword);
        UserModel user = UserModel.create(loginId, encodedPassword, birthDate, name, email);
        return userJpaRepository.save(user);
    }

    @DisplayName("회원 가입 테스트 (POST /users)")
    @Nested
    class SignUp {

        @Test
        @DisplayName("회원가입에 성공 테스트")
        void success() {
            // arrange
            String loginId = "yktest";
            String loginPw = "Password1!";
            UsersSignUpRequestDto requestDto = new UsersSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "김용권",
                "test@google.com"
            );

            HttpHeaders headers = createHeaders(loginId, loginPw);
            HttpEntity<UsersSignUpRequestDto> request = new HttpEntity<>(requestDto, headers);

            // act
            ParameterizedTypeReference<ApiResponse<UserDto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserDto.SignUpResponse>> response =
                testRestTemplate.exchange(ENDPOINT_USERS, HttpMethod.POST, request, responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isNotNull()
            );
        }

        @Test
        @DisplayName("이미 존재하는 로그인 아이디면 400 응답 받는 실패 테스트")
        void fail_when_loginId_already_exists() {
            // arrange
            createUser("existinguser", "Password1!", LocalDate.of(1990, 1, 1), "기존유저", "existing@google.com");

            UsersSignUpRequestDto requestDto = new UsersSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "김용권",
                "new@google.com"
            );

            HttpHeaders headers = createHeaders("existinguser", "Password1!");
            HttpEntity<UsersSignUpRequestDto> request = new HttpEntity<>(requestDto, headers);

            // act
            ParameterizedTypeReference<ApiResponse<UserDto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserDto.SignUpResponse>> response =
                testRestTemplate.exchange(ENDPOINT_USERS, HttpMethod.POST, request, responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("이미 존재하는 이메일이면 400 응답을 받는다")
        void fail_when_email_already_exists() {
            // arrange
            createUser("existinguser", "Password1!", LocalDate.of(1990, 1, 1), "기존유저", "existing@google.com");

            UsersSignUpRequestDto requestDto = new UsersSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "김용권",
                "existing@google.com"  // 같은 이메일 (중복!)
            );

            HttpHeaders headers = createHeaders("newuser", "Password1!");
            HttpEntity<UsersSignUpRequestDto> request = new HttpEntity<>(requestDto, headers);

            // act
            ParameterizedTypeReference<ApiResponse<UserDto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserDto.SignUpResponse>> response =
                testRestTemplate.exchange(ENDPOINT_USERS, HttpMethod.POST, request, responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("비밀번호에 생년월일이 포함되면 400 응답을 받는다")
        void fail_when_password_contains_birthDate() {
            // arrange
            UsersSignUpRequestDto requestDto = new UsersSignUpRequestDto(
                LocalDate.of(1991, 12, 3),
                "김용권",
                "test@google.com"
            );

            HttpHeaders headers = createHeaders("testuser", "Pass19911203!");
            HttpEntity<UsersSignUpRequestDto> request = new HttpEntity<>(requestDto, headers);

            // act
            ParameterizedTypeReference<ApiResponse<UserDto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserDto.SignUpResponse>> response =
                testRestTemplate.exchange(ENDPOINT_USERS, HttpMethod.POST, request, responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("내 정보 조회 테스트 (GET /users/me)")
    @Nested
    class GetMe {

        @Test
        @DisplayName("내 정보 조회 성공 테스트")
        void success() {
            // arrange
            String loginId = "testuser";
            String rawPassword = "Password1!";
            createUser(loginId, rawPassword, LocalDate.of(1991, 12, 3), "김용권", "test@google.com");

            HttpHeaders headers = createHeaders(loginId, rawPassword);
            HttpEntity<Void> request = new HttpEntity<>(null, headers);

            // act
            ParameterizedTypeReference<ApiResponse<UserDto.MyInfoResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<UserDto.MyInfoResponse>> response =
                testRestTemplate.exchange(ENDPOINT_USERS_ME, HttpMethod.GET, request, responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo(loginId),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@google.com")
            );
        }
    }

    @DisplayName("비밀번호 변경 테스트 (PATCH /users/me/password)")
    @Nested
    class ChangePassword {

        @Test
        @DisplayName("비밀번호 변경 성공 테스트")
        void success() {
            // arrange
            String loginId = "testuser";
            String currentPassword = "Password1!";
            String newPassword = "NewPassword1!";
            createUser(loginId, currentPassword, LocalDate.of(1991, 12, 3), "김용권", "test@google.com");

            ChangePasswordRequest requestDto = new ChangePasswordRequest(currentPassword, newPassword);
            HttpHeaders headers = createHeaders(loginId, currentPassword);
            HttpEntity<ChangePasswordRequest> request = new HttpEntity<>(requestDto, headers);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_USERS_ME_PASSWORD, HttpMethod.PATCH, request, responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("현재 비밀번호가 틀리면 400 응답을 받는다")
        void fail_when_current_password_not_match() {
            // arrange
            String loginId = "testuser";
            String currentPassword = "Password1!";
            createUser(loginId, currentPassword, LocalDate.of(1991, 12, 3), "김용권", "test@google.com");

            ChangePasswordRequest requestDto = new ChangePasswordRequest("WrongPassword1!", "NewPassword1!");
            HttpHeaders headers = createHeaders(loginId, currentPassword);
            HttpEntity<ChangePasswordRequest> request = new HttpEntity<>(requestDto, headers);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_USERS_ME_PASSWORD, HttpMethod.PATCH, request, responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("새 비밀번호가 기존 비밀번호와 같으면 400 응답을 받는다")
        void fail_when_new_password_same_as_current() {
            // arrange
            String loginId = "testuser";
            String currentPassword = "Password1!";
            createUser(loginId, currentPassword, LocalDate.of(1991, 12, 3), "김용권", "test@google.com");

            ChangePasswordRequest requestDto = new ChangePasswordRequest(currentPassword, currentPassword);
            HttpHeaders headers = createHeaders(loginId, currentPassword);
            HttpEntity<ChangePasswordRequest> request = new HttpEntity<>(requestDto, headers);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_USERS_ME_PASSWORD, HttpMethod.PATCH, request, responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("새 비밀번호에 생년월일이 포함되면 400 응답을 받는다")
        void fail_when_new_password_contains_birthDate() {
            // arrange
            String loginId = "testuser";
            String currentPassword = "Password1!";
            createUser(loginId, currentPassword, LocalDate.of(1991, 12, 3), "김용권", "test@google.com");

            ChangePasswordRequest requestDto = new ChangePasswordRequest(currentPassword, "Pass19911203!");
            HttpHeaders headers = createHeaders(loginId, currentPassword);
            HttpEntity<ChangePasswordRequest> request = new HttpEntity<>(requestDto, headers);

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_USERS_ME_PASSWORD, HttpMethod.PATCH, request, responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
