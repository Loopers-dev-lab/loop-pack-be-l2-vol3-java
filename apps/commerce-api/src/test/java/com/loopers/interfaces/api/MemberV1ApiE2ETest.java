package com.loopers.interfaces.api;

import com.loopers.domain.member.MemberModel;
import com.loopers.infrastructure.member.MemberJpaRepository;
import com.loopers.interfaces.api.member.MemberV1Dto;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberV1ApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private static final String ENDPOINT_MEMBER = "/api/v1/member";
    private static final String ENDPOINT_CHANGE_PASSWORD = "/api/v1/member/password";

    private final TestRestTemplate testRestTemplate;
    private final MemberJpaRepository memberJpaRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public MemberV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        MemberJpaRepository memberJpaRepository,
        PasswordEncoder passwordEncoder,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.memberJpaRepository = memberJpaRepository;
        this.passwordEncoder = passwordEncoder;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders createAuthHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, password);
        return headers;
    }

    @DisplayName("POST /api/v1/member - 회원가입")
    @Nested
    class SignUp {

        @DisplayName("유효한 정보로 회원가입하면 성공한다")
        @Test
        void signUp_success() {
            // arrange
            MemberV1Dto.SignUpRequest request = new MemberV1Dto.SignUpRequest(
                "testuser",
                "Password123!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_MEMBER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());

            // DB 검증
            MemberModel saved = memberJpaRepository.findByLoginId("testuser").orElseThrow();
            assertThat(saved.getLoginId()).isEqualTo("testuser");
            assertThat(saved.getName()).isEqualTo("홍길동");
            assertThat(saved.getEmail()).isEqualTo("test@example.com");
            assertThat(passwordEncoder.matches("Password123!", saved.getPassword())).isTrue();
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면 409 CONFLICT 응답을 받는다")
        @Test
        void signUp_duplicateLoginId() {
            // arrange - 먼저 회원 생성
            memberJpaRepository.save(MemberModel.signUp(
                "existinguser",
                passwordEncoder.encode("Pass1234!"),
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "existing@example.com"
            ));

            MemberV1Dto.SignUpRequest request = new MemberV1Dto.SignUpRequest(
                "existinguser",
                "Password123!",
                "김철수",
                LocalDate.of(1985, 5, 20),
                "new@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_MEMBER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );
        }

        @DisplayName("이미 존재하는 이메일로 가입하면 409 CONFLICT 응답을 받는다")
        @Test
        void signUp_duplicateEmail() {
            // arrange - 먼저 회원 생성
            memberJpaRepository.save(MemberModel.signUp(
                "firstuser",
                passwordEncoder.encode("Pass1234!"),
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "duplicate@example.com"
            ));

            MemberV1Dto.SignUpRequest request = new MemberV1Dto.SignUpRequest(
                "newuser",
                "Password123!",
                "김철수",
                LocalDate.of(1985, 5, 20),
                "duplicate@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(ENDPOINT_MEMBER, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );
        }
    }

    @DisplayName("GET /api/v1/member - 내 정보 조회")
    @Nested
    class GetMe {

        @DisplayName("올바른 인증 정보로 조회하면 회원 정보를 반환한다")
        @Test
        void getMe_success() {
            // arrange
            String password = "Pass1234!";
            memberJpaRepository.save(MemberModel.signUp(
                "testuser",
                passwordEncoder.encode(password),
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            ));

            HttpHeaders headers = createAuthHeaders("testuser", password);

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response =
                testRestTemplate.exchange(ENDPOINT_MEMBER, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(1990, 1, 15))
            );
        }

        @DisplayName("존재하지 않는 로그인 ID로 조회하면 401 UNAUTHORIZED 응답을 받는다")
        @Test
        void getMe_notFound() {
            // arrange
            HttpHeaders headers = createAuthHeaders("nonexistent", "Password123!");

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response =
                testRestTemplate.exchange(ENDPOINT_MEMBER, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }

        @DisplayName("비밀번호가 틀리면 401 UNAUTHORIZED 응답을 받는다")
        @Test
        void getMe_wrongPassword() {
            // arrange
            memberJpaRepository.save(MemberModel.signUp(
                "testuser",
                passwordEncoder.encode("Correct1234!"),
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            ));

            HttpHeaders headers = createAuthHeaders("testuser", "Wrong12345!");

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response =
                testRestTemplate.exchange(ENDPOINT_MEMBER, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }
    }

    @DisplayName("PUT /api/v1/member/password - 비밀번호 변경")
    @Nested
    class ChangePassword {

        @DisplayName("올바른 인증 정보로 비밀번호 변경하면 성공한다")
        @Test
        void changePassword_success() {
            // arrange
            String currentPassword = "OldPass123!";
            String newPassword = "NewPass456!";

            memberJpaRepository.save(MemberModel.signUp(
                "testuser",
                passwordEncoder.encode(currentPassword),
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            ));

            HttpHeaders headers = createAuthHeaders("testuser", currentPassword);
            MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest(
                currentPassword,
                newPassword
            );

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PUT, new HttpEntity<>(request, headers), responseType);

            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());

            // DB 검증
            MemberModel updated = memberJpaRepository.findByLoginId("testuser").orElseThrow();
            assertThat(passwordEncoder.matches(newPassword, updated.getPassword())).isTrue();
            assertThat(passwordEncoder.matches(currentPassword, updated.getPassword())).isFalse();
        }

        @DisplayName("헤더 인증 비밀번호가 틀리면 401 UNAUTHORIZED 응답을 받는다")
        @Test
        void changePassword_wrongHeaderPassword() {
            // arrange
            memberJpaRepository.save(MemberModel.signUp(
                "testuser",
                passwordEncoder.encode("Correct1234!"),
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            ));

            HttpHeaders headers = createAuthHeaders("testuser", "Wrong12345!");
            MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest(
                "Correct1234!",
                "NewPass1234!"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PUT, new HttpEntity<>(request, headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }

        @DisplayName("현재 비밀번호가 틀리면 400 BAD_REQUEST 응답을 받는다")
        @Test
        void changePassword_wrongCurrentPassword() {
            // arrange
            String actualPassword = "Correct1234!";
            memberJpaRepository.save(MemberModel.signUp(
                "testuser",
                passwordEncoder.encode(actualPassword),
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            ));

            HttpHeaders headers = createAuthHeaders("testuser", actualPassword);
            MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest(
                "Wrong12345!",
                "NewPass1234!"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PUT, new HttpEntity<>(request, headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 400 BAD_REQUEST 응답을 받는다")
        @Test
        void changePassword_samePassword() {
            // arrange
            String samePassword = "SamePass1234!";

            memberJpaRepository.save(MemberModel.signUp(
                "testuser",
                passwordEncoder.encode(samePassword),
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            ));

            HttpHeaders headers = createAuthHeaders("testuser", samePassword);
            MemberV1Dto.ChangePasswordRequest request = new MemberV1Dto.ChangePasswordRequest(
                samePassword,
                samePassword
            );

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PUT, new HttpEntity<>(request, headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }
    }
}
