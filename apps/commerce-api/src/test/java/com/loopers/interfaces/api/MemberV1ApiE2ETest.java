package com.loopers.interfaces.api;

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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api/v1/members";
    private static final String ENDPOINT_ME = "/api/v1/members/me";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final MemberJpaRepository memberJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public MemberV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        MemberJpaRepository memberJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.memberJpaRepository = memberJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/members (회원가입)")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 회원가입하면, 201 Created 응답을 받는다.")
        @Test
        void returnsCreated_whenValidRequest() {
            // arrange
            MemberV1Dto.RegisterRequest request = new MemberV1Dto.RegisterRequest(
                "testUser1",
                "Test1234!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("testUser1"),
                () -> assertThat(memberJpaRepository.existsByLoginId("testUser1")).isTrue()
            );
        }

        @DisplayName("이미 존재하는 로그인ID로 가입하면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenLoginIdAlreadyExists() {
            // arrange - 먼저 회원가입
            MemberV1Dto.RegisterRequest firstRequest = new MemberV1Dto.RegisterRequest(
                "existingUser",
                "Test1234!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "first@example.com"
            );
            testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                new HttpEntity<>(firstRequest),
                new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            );

            // arrange - 같은 로그인ID로 다시 가입 시도
            MemberV1Dto.RegisterRequest duplicateRequest = new MemberV1Dto.RegisterRequest(
                "existingUser",
                "Test5678!",
                "김철수",
                LocalDate.of(1985, 5, 20),
                "second@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                new HttpEntity<>(duplicateRequest),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("잘못된 이메일 형식으로 가입하면, 400 Bad Request 응답을 받는다.")
        @Test
        void returnsBadRequest_whenInvalidEmail() {
            // arrange
            MemberV1Dto.RegisterRequest request = new MemberV1Dto.RegisterRequest(
                "testUser2",
                "Test1234!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "invalid-email"
            );

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/members/me (내 정보 조회)")
    @Nested
    class GetMe {

        @DisplayName("유효한 인증 헤더로 조회하면, 200 OK와 마스킹된 이름을 반환한다.")
        @Test
        void returnsOk_whenValidAuth() {
            // arrange - 먼저 회원가입
            String loginId = "testUser1";
            String password = "Test1234!";
            MemberV1Dto.RegisterRequest registerRequest = new MemberV1Dto.RegisterRequest(
                loginId,
                password,
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );
            testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                new HttpEntity<>(registerRequest),
                new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            );

            // arrange - 인증 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, password);

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo(loginId),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),  // 마스킹 확인
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 Unauthorized 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenNoAuthHeader() {
            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                new HttpEntity<>(null),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("잘못된 비밀번호로 조회하면, 401 Unauthorized 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenWrongPassword() {
            // arrange - 먼저 회원가입
            String loginId = "testUser2";
            MemberV1Dto.RegisterRequest registerRequest = new MemberV1Dto.RegisterRequest(
                loginId,
                "Test1234!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test2@example.com"
            );
            testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                new HttpEntity<>(registerRequest),
                new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
            );

            // arrange - 잘못된 비밀번호로 인증 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.set(HEADER_LOGIN_ID, loginId);
            headers.set(HEADER_LOGIN_PW, "WrongPassword1!");

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ME,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

}
