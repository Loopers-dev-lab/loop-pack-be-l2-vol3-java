package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.utils.DatabaseCleanUp;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/members";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public MemberV1ApiE2ETest(
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

    @DisplayName("POST /api/v1/members (회원가입)")
    @Nested
    class SignUp {

        @DisplayName("유효한 회원 정보를 보내면, 201 Created와 생성된 ID를 반환한다.")
        @Test
        void returnsCreated_whenValidMemberInfoIsProvided() {
            // arrange
            Map<String, String> request = Map.of(
                "loginId", "testuser",
                "password", "Test1234!",
                "name", "홍길동",
                "birthDate", "19900101",
                "email", "test@example.com"
            );

            // act
            ResponseEntity<ApiResponse<Map<String, String>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> {
                  Assertions.assertNotNull(response.getBody());
                  assertThat(response.getBody().data()).isNotNull();
                },
                () -> {
                  Assertions.assertNotNull(response.getBody());
                  assertThat(response.getBody().data().get("loginId")).isNotNull();
                }
            );
        }

        @DisplayName("중복된 loginId로 가입하면, 409 Conflict를 반환한다.")
        @Test
        void returnsConflict_whenDuplicateLoginIdIsProvided() {
            // arrange - 먼저 한 명 가입
            Map<String, String> request = Map.of(
                "loginId", "testuser",
                "password", "Test1234!",
                "name", "홍길동",
                "birthDate", "19900101",
                "email", "test@example.com"
            );
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {});

            // act - 같은 ID로 다시 가입
            ResponseEntity<ApiResponse<Map<String, String>>> response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("GET /api/v1/members/me (회원정보조회)")
    @Nested
    class GetMemberInfo {

        @DisplayName("헤더 인증 성공 시, 200 OK와 마스킹된 이름을 반환한다.")
        @Test
        void returnsMemberInfo_whenHeaderAuthIsValid() {
            // arrange - 먼저 회원 가입
            Map<String, String> signUpRequest = Map.of(
                "loginId", "testuser",
                "password", "Test1234!",
                "name", "홍길동",
                "birthDate", "19900101",
                "email", "test@test.co.kr"
            );
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(signUpRequest), new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {});

            // act - 헤더에 인증 정보 포함하여 조회
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "Test1234!");

            ResponseEntity<ApiResponse<Map<String, String>>> response = testRestTemplate.exchange(
                ENDPOINT + "/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> {
                  Assertions.assertNotNull(response.getBody());
                  assertThat(response.getBody().data()).isNotNull();
                },
                () -> {
                  Assertions.assertNotNull(response.getBody());
                  assertThat(response.getBody().data().get("loginId")).isEqualTo("testuser");
                },
                () -> {
                  Assertions.assertNotNull(response.getBody());
                  assertThat(response.getBody().data().get("name")).isEqualTo("홍길*");
                }
            );
        }

        @DisplayName("헤더 인증 실패 시 (비밀번호 불일치), 401 Unauthorized를 반환한다.")
        @Test
        void returnsUnauthorized_whenHeaderPasswordIsWrong() {
            // arrange - 먼저 회원 가입
            Map<String, String> signUpRequest = Map.of(
                "loginId", "testuser",
                "password", "Test1234!",
                "name", "홍길동",
                "birthDate", "19900101",
                "email", "test@test.co.kr"
            );
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(signUpRequest), new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {});

            // act - 틀린 비밀번호로 조회
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "WrongPass1!");

            ResponseEntity<ApiResponse<Map<String, String>>> response = testRestTemplate.exchange(
                ENDPOINT + "/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 회원의 ID로 조회하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenMemberDoesNotExist() {
            // arrange - 아무 데이터 없음
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "nonexistent");
            headers.set("X-Loopers-LoginPw", "Test1234!");

            // act
            ResponseEntity<ApiResponse<Map<String, String>>> response = testRestTemplate.exchange(
                ENDPOINT + "/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PATCH /api/v1/members/me/password (비밀번호 변경)")
    @Nested
    class ChangePassword {

        @DisplayName("헤더 인증 성공 + 유효한 비밀번호 변경 요청이면, 200 OK를 반환한다.")
        @Test
        void returnsOk_whenHeaderAuthAndPasswordChangeAreValid() {
            // arrange - 먼저 회원 가입
            Map<String, String> signUpRequest = Map.of(
                "loginId", "testuser",
                "password", "Test1234!",
                "name", "홍길동",
                "birthDate", "19900101",
                "email", "test@test.co.kr"
            );
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(signUpRequest), new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {});

            // act - 헤더 인증 + 비밀번호 변경 요청
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "Test1234!");
            headers.set("Content-Type", "application/json");

            Map<String, String> changePasswordRequest = Map.of(
                "oldPassword", "Test1234!",
                "newPassword", "NewPass123!"
            );
            ResponseEntity<ApiResponse<String>> response = testRestTemplate.exchange(
                ENDPOINT + "/me/password",
                HttpMethod.PATCH,
                new HttpEntity<>(changePasswordRequest, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> {
                  Assertions.assertNotNull(response.getBody());
                  assertThat(response.getBody().data()).isEqualTo("비밀번호가 변경되었습니다.");
                }
            );
        }

        @DisplayName("헤더 인증 실패 시 (비밀번호 불일치), 401 Unauthorized를 반환한다.")
        @Test
        void returnsUnauthorized_whenHeaderPasswordIsWrong() {
            // arrange - 먼저 회원 가입
            Map<String, String> signUpRequest = Map.of(
                "loginId", "testuser",
                "password", "Test1234!",
                "name", "홍길동",
                "birthDate", "19900101",
                "email", "test@test.co.kr"
            );
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(signUpRequest), new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {});

            // act - 틀린 헤더 비밀번호로 요청
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "WrongPass1!");
            headers.set("Content-Type", "application/json");

            Map<String, String> changePasswordRequest = Map.of(
                "oldPassword", "Test1234!",
                "newPassword", "NewPass123!"
            );
            ResponseEntity<ApiResponse<String>> response = testRestTemplate.exchange(
                ENDPOINT + "/me/password",
                HttpMethod.PATCH,
                new HttpEntity<>(changePasswordRequest, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("Body의 기존 비밀번호가 일치하지 않으면, 401 Unauthorized를 반환한다.")
        @Test
        void returnsUnauthorized_whenOldPasswordInBodyDoesNotMatch() {
            // arrange - 먼저 회원 가입
            Map<String, String> signUpRequest = Map.of(
                "loginId", "testuser",
                "password", "Test1234!",
                "name", "홍길동",
                "birthDate", "19900101",
                "email", "test@test.co.kr"
            );
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(signUpRequest), new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {});

            // act - 헤더는 맞지만 Body의 oldPassword가 틀림
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "Test1234!");
            headers.set("Content-Type", "application/json");

            Map<String, String> changePasswordRequest = Map.of(
                "oldPassword", "WrongPass1!",
                "newPassword", "NewPass123!"
            );
            ResponseEntity<ApiResponse<String>> response = testRestTemplate.exchange(
                ENDPOINT + "/me/password",
                HttpMethod.PATCH,
                new HttpEntity<>(changePasswordRequest, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 같으면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenNewPasswordIsSameAsOld() {
            // arrange - 먼저 회원 가입
            Map<String, String> signUpRequest = Map.of(
                "loginId", "testuser",
                "password", "Test1234!",
                "name", "홍길동",
                "birthDate", "19900101",
                "email", "test@test.co.kr"
            );
            testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(signUpRequest), new ParameterizedTypeReference<ApiResponse<Map<String, String>>>() {});

            // act - 기존과 동일한 비밀번호로 변경 요청
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "Test1234!");
            headers.set("Content-Type", "application/json");

            Map<String, String> changePasswordRequest = Map.of(
                "oldPassword", "Test1234!",
                "newPassword", "Test1234!"
            );
            ResponseEntity<ApiResponse<String>> response = testRestTemplate.exchange(
                ENDPOINT + "/me/password",
                HttpMethod.PATCH,
                new HttpEntity<>(changePasswordRequest, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
