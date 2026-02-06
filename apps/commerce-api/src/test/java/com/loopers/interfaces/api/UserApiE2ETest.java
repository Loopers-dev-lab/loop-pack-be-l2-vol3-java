package com.loopers.interfaces.api;

import com.loopers.interfaces.api.dto.PasswordUpdateRequest;
import com.loopers.interfaces.api.dto.UserInfoResponse;
import com.loopers.interfaces.api.dto.UserRegisterRequest;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
class UserApiE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final String BASE_URL = "/api/v1/users";
    private static final LocalDate TEST_BIRTHDAY = LocalDate.of(1990, 5, 15);

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("E2E: 회원가입 시나리오")
    class RegisterE2E {

        @Test
        @DisplayName("회원가입 → 내 정보 조회 성공")
        void register_then_getMyInfo() {
            // given
            String loginId = "e2euser1";
            String password = "Password1!";
            var registerRequest = createRegisterRequest(loginId, password, "홍길동");

            // when - 회원가입
            ResponseEntity<Void> registerResponse = restTemplate.postForEntity(
                    BASE_URL + "/register",
                    registerRequest,
                    Void.class
            );

            // then - 회원가입 성공
            assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // when - 내 정보 조회
            HttpHeaders headers = createAuthHeaders(loginId, password);
            ResponseEntity<UserInfoResponse> getInfoResponse = restTemplate.exchange(
                    BASE_URL + "/me",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    UserInfoResponse.class
            );

            // then - 조회 성공
            assertThat(getInfoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getInfoResponse.getBody()).isNotNull();
            assertThat(getInfoResponse.getBody().loginId()).isEqualTo(loginId);
            assertThat(getInfoResponse.getBody().name()).isEqualTo("홍길*");
            assertThat(getInfoResponse.getBody().birthday()).isEqualTo("19900515");
        }

        @Test
        @DisplayName("중복 ID 가입 시도 실패")
        void register_duplicateId_fail() {
            // given
            String loginId = "e2euser1";
            var request = createRegisterRequest(loginId, "Password1!", "홍길동");

            // 첫 번째 가입
            restTemplate.postForEntity(BASE_URL + "/register", request, Void.class);

            // when - 동일 ID로 재가입
            ResponseEntity<Void> response = restTemplate.postForEntity(
                    BASE_URL + "/register",
                    request,
                    Void.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("E2E: 인증 시나리오")
    class AuthenticationE2E {

        @Test
        @DisplayName("잘못된 비밀번호로 인증 실패")
        void authentication_wrongPassword_fail() {
            // given
            String loginId = "e2euser1";
            registerUser(loginId, "Password1!", "홍길동");

            // when - 잘못된 비밀번호로 조회
            HttpHeaders headers = createAuthHeaders(loginId, "WrongPassword1!");
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/me",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("존재하지 않는 사용자 인증 실패")
        void authentication_userNotFound_fail() {
            // when
            HttpHeaders headers = createAuthHeaders("notexist", "Password1!");
            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/me",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("E2E: 비밀번호 변경 시나리오")
    class PasswordChangeE2E {

        @Test
        @DisplayName("비밀번호 변경 → 새 비밀번호로 로그인 성공")
        void changePassword_then_loginWithNewPassword() {
            // given
            String loginId = "e2euser1";
            String oldPassword = "Password1!";
            String newPassword = "NewPassword1!";
            registerUser(loginId, oldPassword, "홍길동");

            // when - 비밀번호 변경
            HttpHeaders headers = createAuthHeaders(loginId, oldPassword);
            headers.setContentType(MediaType.APPLICATION_JSON);
            var updateRequest = new PasswordUpdateRequest(oldPassword, newPassword);

            ResponseEntity<Void> updateResponse = restTemplate.exchange(
                    BASE_URL + "/me/password",
                    HttpMethod.PUT,
                    new HttpEntity<>(updateRequest, headers),
                    Void.class
            );

            // then - 변경 성공
            assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // when - 새 비밀번호로 조회
            HttpHeaders newHeaders = createAuthHeaders(loginId, newPassword);
            ResponseEntity<UserInfoResponse> getInfoResponse = restTemplate.exchange(
                    BASE_URL + "/me",
                    HttpMethod.GET,
                    new HttpEntity<>(newHeaders),
                    UserInfoResponse.class
            );

            // then - 새 비밀번호로 조회 성공
            assertThat(getInfoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // when - 이전 비밀번호로 조회 시도
            HttpHeaders oldHeaders = createAuthHeaders(loginId, oldPassword);
            ResponseEntity<String> oldPasswordResponse = restTemplate.exchange(
                    BASE_URL + "/me",
                    HttpMethod.GET,
                    new HttpEntity<>(oldHeaders),
                    String.class
            );

            // then - 이전 비밀번호로는 실패
            assertThat(oldPasswordResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("동일한 비밀번호로 변경 시 실패")
        void changePassword_samePassword_fail() {
            // given
            String loginId = "e2euser1";
            String password = "Password1!";
            registerUser(loginId, password, "홍길동");

            // when
            HttpHeaders headers = createAuthHeaders(loginId, password);
            headers.setContentType(MediaType.APPLICATION_JSON);
            var updateRequest = new PasswordUpdateRequest(password, password);

            ResponseEntity<String> response = restTemplate.exchange(
                    BASE_URL + "/me/password",
                    HttpMethod.PUT,
                    new HttpEntity<>(updateRequest, headers),
                    String.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("E2E: 전체 사용자 플로우")
    class FullUserFlowE2E {

        @Test
        @DisplayName("회원가입 → 조회 → 비밀번호 변경 → 새 비밀번호로 조회")
        void fullUserFlow() {
            // Step 1: 회원가입
            String loginId = "flowuser1";
            String password = "Password1!";
            var registerRequest = createRegisterRequest(loginId, password, "김철수");

            ResponseEntity<Void> registerResponse = restTemplate.postForEntity(
                    BASE_URL + "/register",
                    registerRequest,
                    Void.class
            );
            assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Step 2: 내 정보 조회
            HttpHeaders headers = createAuthHeaders(loginId, password);
            ResponseEntity<UserInfoResponse> infoResponse = restTemplate.exchange(
                    BASE_URL + "/me",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    UserInfoResponse.class
            );
            assertThat(infoResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(infoResponse.getBody().name()).isEqualTo("김철*");

            // Step 3: 비밀번호 변경
            String newPassword = "NewPassword1!";
            headers.setContentType(MediaType.APPLICATION_JSON);
            var updateRequest = new PasswordUpdateRequest(password, newPassword);

            ResponseEntity<Void> updateResponse = restTemplate.exchange(
                    BASE_URL + "/me/password",
                    HttpMethod.PUT,
                    new HttpEntity<>(updateRequest, headers),
                    Void.class
            );
            assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // Step 4: 새 비밀번호로 조회
            HttpHeaders newHeaders = createAuthHeaders(loginId, newPassword);
            ResponseEntity<UserInfoResponse> finalResponse = restTemplate.exchange(
                    BASE_URL + "/me",
                    HttpMethod.GET,
                    new HttpEntity<>(newHeaders),
                    UserInfoResponse.class
            );
            assertThat(finalResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(finalResponse.getBody().loginId()).isEqualTo(loginId);
        }
    }

    private UserRegisterRequest createRegisterRequest(String loginId, String password, String name) {
        return new UserRegisterRequest(
                loginId,
                password,
                name,
                TEST_BIRTHDAY,
                "test@example.com"
        );
    }

    private HttpHeaders createAuthHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    private void registerUser(String loginId, String password, String name) {
        var request = createRegisterRequest(loginId, password, name);
        restTemplate.postForEntity(BASE_URL + "/register", request, Void.class);
    }
}
