package com.loopers.e2e;

import com.loopers.interfaces.api.dto.PasswordUpdateRequest;
import com.loopers.interfaces.api.dto.UserInfoResponse;
import com.loopers.interfaces.api.dto.UserRegisterRequest;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(MySqlTestContainersConfig.class)
class UserApiE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/users";
    }

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("회원가입 API 성공")
    void register_success() {
        // given
        UserRegisterRequest request = new UserRegisterRequest(
                "e2euser1",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 5, 15),
                "e2e@example.com"
        );

        // when
        ResponseEntity<Void> response = restTemplate.postForEntity(
                baseUrl() + "/register",
                request,
                Void.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("회원가입 API 실패 - 중복 ID")
    void register_fail_duplicate_id() {
        // given - 먼저 가입
        UserRegisterRequest request = new UserRegisterRequest(
                "dupuser1",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 5, 15),
                "dup@example.com"
        );
        restTemplate.postForEntity(baseUrl() + "/register", request, Void.class);

        // when - 같은 ID로 다시 가입 시도
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/register",
                request,
                String.class
        );

        // then
        assertThat(response.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("회원가입 API 실패 - 잘못된 ID 형식")
    void register_fail_invalid_userId() {
        // given
        UserRegisterRequest request = new UserRegisterRequest(
                "ab",  // 4자 미만
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 5, 15),
                "invalid@example.com"
        );

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/register",
                request,
                String.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("회원가입 API 실패 - 잘못된 비밀번호 형식")
    void register_fail_invalid_password() {
        // given
        UserRegisterRequest request = new UserRegisterRequest(
                "e2euser2",
                "short",  // 8자 미만
                "홍길동",
                LocalDate.of(1990, 5, 15),
                "short@example.com"
        );

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl() + "/register",
                request,
                String.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("내 정보 조회 API 성공")
    void getMyInfo_success() {
        // given - 먼저 가입
        String loginId = "infouser";
        String password = "Password1!";

        UserRegisterRequest registerRequest = new UserRegisterRequest(
                loginId,
                password,
                "홍길동",
                LocalDate.of(1990, 5, 15),
                "info@example.com"
        );
        restTemplate.postForEntity(baseUrl() + "/register", registerRequest, Void.class);

        // when
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);

        ResponseEntity<UserInfoResponse> response = restTemplate.exchange(
                baseUrl() + "/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                UserInfoResponse.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().loginId()).isEqualTo(loginId);
        assertThat(response.getBody().name()).isEqualTo("홍길*");  // 마스킹
        assertThat(response.getBody().birthday()).isEqualTo("19900515");
        assertThat(response.getBody().email()).isEqualTo("info@example.com");
    }

    @Test
    @DisplayName("내 정보 조회 API - 영문 이름 마스킹")
    void getMyInfo_english_name_masking() {
        // given
        String loginId = "johnuser";
        String password = "Password1!";

        UserRegisterRequest registerRequest = new UserRegisterRequest(
                loginId,
                password,
                "John",
                LocalDate.of(1990, 5, 15),
                "john@example.com"
        );
        restTemplate.postForEntity(baseUrl() + "/register", registerRequest, Void.class);

        // when
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);

        ResponseEntity<UserInfoResponse> response = restTemplate.exchange(
                baseUrl() + "/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                UserInfoResponse.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("Joh*");
    }

    @Test
    @DisplayName("비밀번호 수정 API 성공")
    void updatePassword_success() {
        // given - 먼저 가입
        String loginId = "pwduser1";
        String oldPassword = "OldPass1!";
        String newPassword = "NewPass1!";

        UserRegisterRequest registerRequest = new UserRegisterRequest(
                loginId,
                oldPassword,
                "홍길동",
                LocalDate.of(1990, 5, 15),
                "pwd@example.com"
        );
        restTemplate.postForEntity(baseUrl() + "/register", registerRequest, Void.class);

        // when
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", oldPassword);
        headers.setContentType(MediaType.APPLICATION_JSON);

        PasswordUpdateRequest updateRequest = new PasswordUpdateRequest(
                oldPassword,
                newPassword
        );

        ResponseEntity<Void> response = restTemplate.exchange(
                baseUrl() + "/me/password",
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest, headers),
                Void.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("비밀번호 수정 API 실패 - 현재 비밀번호 불일치")
    void updatePassword_fail_wrong_current() {
        // given - 먼저 가입
        String loginId = "pwduser2";
        String correctPassword = "Correct1!";

        UserRegisterRequest registerRequest = new UserRegisterRequest(
                loginId,
                correctPassword,
                "홍길동",
                LocalDate.of(1990, 5, 15),
                "pwd2@example.com"
        );
        restTemplate.postForEntity(baseUrl() + "/register", registerRequest, Void.class);

        // when
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", correctPassword);
        headers.setContentType(MediaType.APPLICATION_JSON);

        PasswordUpdateRequest updateRequest = new PasswordUpdateRequest(
                "WrongPwd1!",  // 틀린 현재 비밀번호
                "NewPass1!"
        );

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/me/password",
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest, headers),
                String.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("비밀번호 수정 API 실패 - 새 비밀번호가 현재와 동일")
    void updatePassword_fail_same_password() {
        // given
        String loginId = "pwduser3";
        String password = "SamePass1!";

        UserRegisterRequest registerRequest = new UserRegisterRequest(
                loginId,
                password,
                "홍길동",
                LocalDate.of(1990, 5, 15),
                "pwd3@example.com"
        );
        restTemplate.postForEntity(baseUrl() + "/register", registerRequest, Void.class);

        // when
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        headers.setContentType(MediaType.APPLICATION_JSON);

        PasswordUpdateRequest updateRequest = new PasswordUpdateRequest(
                password,
                password  // 동일한 비밀번호
        );

        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl() + "/me/password",
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest, headers),
                String.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
