package com.loopers.interfaces.api.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.interfaces.api.ApiResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberV1ApiE2ETest {

    private static final String ENDPOINT = "/api/v1/members";

    private final TestRestTemplate testRestTemplate;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public MemberV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        MemberRepository memberRepository,
        PasswordEncoder passwordEncoder,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/members")
    @Nested
    class SignUp {

        @DisplayName("정상적인 정보로 가입하면, 201 Created와 회원 정보를 반환한다.")
        @Test
        void returnsCreated_whenValidRequest() {
            // arrange
            MemberV1Dto.SignUpRequest request = new MemberV1Dto.SignUpRequest(
                "testuser1", "Test1234!", "홍길동", "1995-03-15", "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.SignUpResponse>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().id()).isNotNull(),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser1"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길동"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("필수 필드가 누락되면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenFieldMissing() {
            // arrange
            MemberV1Dto.SignUpRequest request = new MemberV1Dto.SignUpRequest(
                "testuser1", "Test1234!", "홍길동", "1995-03-15", null
            );

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("이미 존재하는 loginId로 가입하면, 409 Conflict를 반환한다.")
        @Test
        void returnsConflict_whenDuplicateLoginId() {
            // arrange
            Member existing = new Member("testuser1", "Test1234!", "홍길동", LocalDate.of(1995, 3, 15), "test@example.com");
            existing.encryptPassword(passwordEncoder.encode("Test1234!"));
            memberRepository.save(existing);

            MemberV1Dto.SignUpRequest request = new MemberV1Dto.SignUpRequest(
                "testuser1", "Other1234!", "김철수", "1990-01-01", "other@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }
}