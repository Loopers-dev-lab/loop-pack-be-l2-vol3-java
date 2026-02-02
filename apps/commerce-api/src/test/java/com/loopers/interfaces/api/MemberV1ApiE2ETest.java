package com.loopers.interfaces.api;

import com.loopers.infrastructure.member.MemberJpaRepository;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberV1ApiE2ETest {

    private static final String ENDPOINT_REGISTER = "/api/v1/members";

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
            RegisterRequest request = new RegisterRequest(
                "testUser1",
                "Test1234!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<RegisterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<RegisterResponse>> response = testRestTemplate.exchange(
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
            RegisterRequest firstRequest = new RegisterRequest(
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
                new ParameterizedTypeReference<ApiResponse<RegisterResponse>>() {}
            );

            // arrange - 같은 로그인ID로 다시 가입 시도
            RegisterRequest duplicateRequest = new RegisterRequest(
                "existingUser",
                "Test5678!",
                "김철수",
                LocalDate.of(1985, 5, 20),
                "second@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<RegisterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<RegisterResponse>> response = testRestTemplate.exchange(
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
            RegisterRequest request = new RegisterRequest(
                "testUser2",
                "Test1234!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "invalid-email"
            );

            // act
            ParameterizedTypeReference<ApiResponse<RegisterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<RegisterResponse>> response = testRestTemplate.exchange(
                ENDPOINT_REGISTER,
                HttpMethod.POST,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // 테스트용 Request/Response record (실제 DTO가 없으므로 임시 정의)
    record RegisterRequest(String loginId, String password, String name, LocalDate birthDate, String email) {}
    record RegisterResponse(String loginId, String name, LocalDate birthDate, String email) {}
}
