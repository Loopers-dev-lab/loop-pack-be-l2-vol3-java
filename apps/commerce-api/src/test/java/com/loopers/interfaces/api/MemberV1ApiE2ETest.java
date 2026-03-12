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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MemberV1ApiE2ETest {

    private static final String ENDPOINT_SIGNUP = "/api/v1/members";
    private static final String ENDPOINT_ME = "/api/v1/members/me";
    private static final String ENDPOINT_CHANGE_PASSWORD = "/api/v1/members/me/password";

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

    private MemberV1Dto.SignupRequest signupRequest() {
        return new MemberV1Dto.SignupRequest(
            "kwonmo", "Test1234!", "양권모",
            LocalDate.of(1998, 9, 16), "kwonmo@example.com"
        );
    }

    private void signupMember() {
        testRestTemplate.exchange(
            ENDPOINT_SIGNUP, HttpMethod.POST,
            new HttpEntity<>(signupRequest()),
            new ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>>() {}
        );
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }

    @DisplayName("POST /api/v1/members")
    @Nested
    class Signup {

        @DisplayName("유효한 정보로 가입하면 200과 회원 정보를 반환한다")
        @Test
        void returns200WithMemberInfo() {
            // given
            MemberV1Dto.SignupRequest request = signupRequest();

            // when
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST,
                    new HttpEntity<>(request), responseType);

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("kwonmo"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("양권모"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("kwonmo@example.com")
            );
        }

        @DisplayName("중복 loginId로 가입하면 409를 반환한다")
        @Test
        void returns409OnDuplicateLoginId() {
            // given
            signupMember();
            MemberV1Dto.SignupRequest duplicateRequest = new MemberV1Dto.SignupRequest(
                "kwonmo", "Other1234!", "박지훈",
                LocalDate.of(1995, 5, 20), "jihun@example.com"
            );

            // when
            ParameterizedTypeReference<ApiResponse<Object>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST,
                    new HttpEntity<>(duplicateRequest), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("잘못된 loginId 형식이면 400을 반환한다")
        @Test
        void returns400OnInvalidLoginId() {
            // given
            MemberV1Dto.SignupRequest request = new MemberV1Dto.SignupRequest(
                "test@user", "Test1234!", "양권모",
                LocalDate.of(1998, 9, 16), "kwonmo@example.com"
            );

            // when
            ParameterizedTypeReference<ApiResponse<Object>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST,
                    new HttpEntity<>(request), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/members/me")
    @Nested
    class GetMe {

        @DisplayName("인증 성공 시 마스킹된 이름으로 반환한다")
        @Test
        void returns200WithMaskedName() {
            // given
            signupMember();

            // when
            ParameterizedTypeReference<ApiResponse<MemberV1Dto.MemberResponse>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<MemberV1Dto.MemberResponse>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET,
                    new HttpEntity<>(null, authHeaders("kwonmo", "Test1234!")),
                    responseType);

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("kwonmo"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("양권*"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("kwonmo@example.com")
            );
        }

        @DisplayName("비밀번호가 틀리면 401을 반환한다")
        @Test
        void returns401OnWrongPassword() {
            // given
            signupMember();

            // when
            ParameterizedTypeReference<ApiResponse<Object>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_ME, HttpMethod.GET,
                    new HttpEntity<>(null, authHeaders("kwonmo", "WrongPass1!")),
                    responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PATCH /api/v1/members/me/password")
    @Nested
    class ChangePassword {

        @DisplayName("유효한 요청이면 200을 반환한다")
        @Test
        void returns200OnValidRequest() {
            // given
            signupMember();
            MemberV1Dto.ChangePasswordRequest request =
                new MemberV1Dto.ChangePasswordRequest("Test1234!", "NewPass5678!");

            // when
            ParameterizedTypeReference<ApiResponse<Object>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH,
                    new HttpEntity<>(request, authHeaders("kwonmo", "Test1234!")),
                    responseType);

            // then
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("현재 비밀번호가 틀리면 400을 반환한다")
        @Test
        void returns400OnWrongCurrentPassword() {
            // given
            signupMember();
            MemberV1Dto.ChangePasswordRequest request =
                new MemberV1Dto.ChangePasswordRequest("WrongPass1!", "NewPass5678!");

            // when
            ParameterizedTypeReference<ApiResponse<Object>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH,
                    new HttpEntity<>(request, authHeaders("kwonmo", "Test1234!")),
                    responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("비밀번호 규칙을 위반하면 400을 반환한다")
        @Test
        void returns400OnInvalidNewPassword() {
            // given
            signupMember();
            MemberV1Dto.ChangePasswordRequest request =
                new MemberV1Dto.ChangePasswordRequest("Test1234!", "short");

            // when
            ParameterizedTypeReference<ApiResponse<Object>> responseType =
                new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_CHANGE_PASSWORD, HttpMethod.PATCH,
                    new HttpEntity<>(request, authHeaders("kwonmo", "Test1234!")),
                    responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }
}
