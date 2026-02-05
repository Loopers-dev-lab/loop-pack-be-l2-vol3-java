package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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
@DisplayName("User V1 API 시나리오 테스트")
class UserV1ApiScenarioTest {

    private static final String ENDPOINT_SIGNUP = "/api/v1/users/signup";
    private static final String ENDPOINT_MY_INFO = "/api/v1/users/me";
    private static final String ENDPOINT_CHANGE_PASSWORD = "/api/v1/users/password";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserV1ApiScenarioTest(
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

    @DisplayName("유저 전체 플로우: 회원가입 -> 내 정보 조회 -> 비밀번호 변경 -> 내 정보 조회")
    @Test
    void fullUserFlowScenario() {
        String loginId = "testuser123";
        String originalPassword = "Test1234!@#";
        String newPassword = "NewPass123!@";
        String name = "홍길동";
        String birthDate = "19900115";
        String email = "test@example.com";

        // ===== 1단계: 회원가입 =====
        UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
            loginId,
            originalPassword,
            name,
            birthDate,
            email
        );

        ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> signupResponseType =
            new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> signupResponse =
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(signupRequest), signupResponseType);

        // 회원가입 검증
        assertAll(
            "회원가입 성공 검증",
            () -> assertTrue(signupResponse.getStatusCode().is2xxSuccessful()),
            () -> assertThat(signupResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(signupResponse.getBody()).isNotNull(),
            () -> assertThat(signupResponse.getBody().data().loginId()).isEqualTo(loginId),
            () -> assertThat(signupResponse.getBody().data().name()).isEqualTo("홍길*"),
            () -> assertThat(signupResponse.getBody().data().birthDate()).isEqualTo(birthDate),
            () -> assertThat(signupResponse.getBody().data().email()).isEqualTo(email)
        );

        // ===== 2단계: 내 정보 조회 (원래 비밀번호로) =====
        HttpHeaders headers1 = new HttpHeaders();
        headers1.set(HEADER_LOGIN_ID, loginId);
        headers1.set(HEADER_LOGIN_PW, originalPassword);

        ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>> myInfoResponseType =
            new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> myInfoResponse1 =
            testRestTemplate.exchange(ENDPOINT_MY_INFO, HttpMethod.GET, new HttpEntity<>(headers1), myInfoResponseType);

        // 첫 번째 내 정보 조회 검증
        assertAll(
            "첫 번째 내 정보 조회 성공 검증",
            () -> assertTrue(myInfoResponse1.getStatusCode().is2xxSuccessful()),
            () -> assertThat(myInfoResponse1.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(myInfoResponse1.getBody()).isNotNull(),
            () -> assertThat(myInfoResponse1.getBody().data().loginId()).isEqualTo(loginId),
            () -> assertThat(myInfoResponse1.getBody().data().name()).isEqualTo("홍길*"),
            () -> assertThat(myInfoResponse1.getBody().data().birthDate()).isEqualTo(birthDate),
            () -> assertThat(myInfoResponse1.getBody().data().email()).isEqualTo(email)
        );

        // ===== 3단계: 비밀번호 변경 =====
        HttpHeaders headers2 = new HttpHeaders();
        headers2.set(HEADER_LOGIN_ID, loginId);
        headers2.set(HEADER_LOGIN_PW, originalPassword);

        UserV1Dto.ChangePasswordRequest changePasswordRequest = new UserV1Dto.ChangePasswordRequest(
            originalPassword,
            newPassword
        );

        ParameterizedTypeReference<ApiResponse<UserV1Dto.ChangePasswordResponse>> changePasswordResponseType =
            new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<UserV1Dto.ChangePasswordResponse>> changePasswordResponse =
            testRestTemplate.exchange(
                ENDPOINT_CHANGE_PASSWORD,
                HttpMethod.PATCH,
                new HttpEntity<>(changePasswordRequest, headers2),
                changePasswordResponseType
            );

        // 비밀번호 변경 검증
        assertAll(
            "비밀번호 변경 성공 검증",
            () -> assertTrue(changePasswordResponse.getStatusCode().is2xxSuccessful()),
            () -> assertThat(changePasswordResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(changePasswordResponse.getBody()).isNotNull(),
            () -> assertThat(changePasswordResponse.getBody().data().message()).isEqualTo("비밀번호가 성공적으로 변경되었습니다.")
        );

        // ===== 4단계: 내 정보 조회 (새 비밀번호로) =====
        HttpHeaders headers3 = new HttpHeaders();
        headers3.set(HEADER_LOGIN_ID, loginId);
        headers3.set(HEADER_LOGIN_PW, newPassword);

        ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> myInfoResponse2 =
            testRestTemplate.exchange(ENDPOINT_MY_INFO, HttpMethod.GET, new HttpEntity<>(headers3), myInfoResponseType);

        // 두 번째 내 정보 조회 검증 (새 비밀번호로 인증 성공)
        assertAll(
            "새 비밀번호로 내 정보 조회 성공 검증",
            () -> assertTrue(myInfoResponse2.getStatusCode().is2xxSuccessful()),
            () -> assertThat(myInfoResponse2.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(myInfoResponse2.getBody()).isNotNull(),
            () -> assertThat(myInfoResponse2.getBody().data().loginId()).isEqualTo(loginId),
            () -> assertThat(myInfoResponse2.getBody().data().name()).isEqualTo("홍길*"),
            () -> assertThat(myInfoResponse2.getBody().data().birthDate()).isEqualTo(birthDate),
            () -> assertThat(myInfoResponse2.getBody().data().email()).isEqualTo(email)
        );

        // ===== 5단계: 이전 비밀번호로는 인증 실패 확인 =====
        HttpHeaders headers4 = new HttpHeaders();
        headers4.set(HEADER_LOGIN_ID, loginId);
        headers4.set(HEADER_LOGIN_PW, originalPassword);

        ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> myInfoResponse3 =
            testRestTemplate.exchange(ENDPOINT_MY_INFO, HttpMethod.GET, new HttpEntity<>(headers4), myInfoResponseType);

        // 이전 비밀번호로 인증 실패 검증
        assertAll(
            "이전 비밀번호로 인증 실패 검증",
            () -> assertTrue(myInfoResponse3.getStatusCode().is4xxClientError()),
            () -> assertThat(myInfoResponse3.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
        );
    }
}
