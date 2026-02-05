package com.loopers.interfaces.api;

import com.loopers.interfaces.api.user.UserV1Dto;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String REGISTER_ENDPOINT = "/api/v1/users/register";
    private static final String USER_INFO_ENDPOINT = "/api/v1/users/info";
    private static final String UPDATE_PASSWORD_ENDPOINT = "/api/v1/users/password";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserV1ApiE2ETest(
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

    private HttpEntity<UserV1Dto.RegisterRequest> createRequestEntity(UserV1Dto.RegisterRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(request, headers);
    }

    @DisplayName("POST /api/v1/users/register")
    @Nested
    class Register {

        @DisplayName("유효한 정보로 회원가입하면, 성공 응답을 반환한다.")
        @Test
        void returnsSuccess_whenValidInfoProvided() {
            // given
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "testuser",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response =
                testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(request), responseType);

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길동"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("이미 존재하는 로그인 ID로 회원가입하면, 409 CONFLICT 응답을 반환한다.")
        @Test
        void returnsConflict_whenLoginIdAlreadyExists() {
            // given
            UserV1Dto.RegisterRequest firstRequest = new UserV1Dto.RegisterRequest(
                "duplicateuser",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "first@example.com"
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> responseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(firstRequest), responseType);

            UserV1Dto.RegisterRequest duplicateRequest = new UserV1Dto.RegisterRequest(
                "duplicateuser",
                "Password2!",
                "김철수",
                LocalDate.of(1995, 5, 20),
                "second@example.com"
            );

            // when
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response =
                testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(duplicateRequest), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("비밀번호가 8자 미만이면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenPasswordTooShort() {
            // given
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "testuser",
                "Pass1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response =
                testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(request), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 16자 초과이면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenPasswordTooLong() {
            // given
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "testuser",
                "Password1!Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response =
                testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(request), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 허용되지 않는 문자(공백)가 포함되면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenPasswordContainsInvalidCharacter() {
            // given
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "testuser",
                "Pass word1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response =
                testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(request), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(yyyyMMdd)이 포함되면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenPasswordContainsBirthDateYYYYMMDD() {
            // given
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "testuser",
                "19900115Pw!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response =
                testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(request), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(yyMMdd)이 포함되면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenPasswordContainsBirthDateYYMMDD() {
            // given
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "testuser",
                "900115Pass!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response =
                testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(request), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("이메일 형식이 올바르지 않으면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenEmailFormatInvalid() {
            // given
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "testuser",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "invalid-email"
            );

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response =
                testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(request), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("이름이 비어있으면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsEmpty() {
            // given
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "testuser",
                "Password1!",
                "",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response =
                testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(request), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("로그인 ID가 비어있으면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenLoginIdIsEmpty() {
            // given
            UserV1Dto.RegisterRequest request = new UserV1Dto.RegisterRequest(
                "",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.RegisterResponse>> response =
                testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(request), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/users/info")
    @Nested
    class GetUserInfo {

        @DisplayName("유효한 인증 정보로 내 정보를 조회하면, 성공 응답을 반환한다.")
        @Test
        void returnsSuccess_whenValidCredentialsProvided() {
            // given
            UserV1Dto.RegisterRequest registerRequest = new UserV1Dto.RegisterRequest(
                "testuser",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> registerResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(registerRequest), registerResponseType);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "Password1!");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserInfoResponse>> response =
                testRestTemplate.exchange(USER_INFO_ENDPOINT, HttpMethod.GET, requestEntity, responseType);

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(1990, 1, 15)),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("존재하지 않는 로그인 ID로 조회하면, 404 NOT_FOUND 응답을 반환한다.")
        @Test
        void returnsNotFound_whenUserNotExists() {
            // given
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "nonexistent");
            headers.set("X-Loopers-LoginPw", "Password1!");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserInfoResponse>> response =
                testRestTemplate.exchange(USER_INFO_ENDPOINT, HttpMethod.GET, requestEntity, responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("비밀번호가 일치하지 않으면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenPasswordNotMatch() {
            // given
            UserV1Dto.RegisterRequest registerRequest = new UserV1Dto.RegisterRequest(
                "testuser",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> registerResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(registerRequest), registerResponseType);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "WrongPassword1!");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserInfoResponse>> response =
                testRestTemplate.exchange(USER_INFO_ENDPOINT, HttpMethod.GET, requestEntity, responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("이름이 한 글자인 경우, 마스킹 처리하여 반환한다.")
        @Test
        void returnsMaskedName_whenNameIsSingleCharacter() {
            // given
            UserV1Dto.RegisterRequest registerRequest = new UserV1Dto.RegisterRequest(
                "testuser",
                "Password1!",
                "김",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> registerResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(registerRequest), registerResponseType);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "Password1!");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserInfoResponse>> response =
                testRestTemplate.exchange(USER_INFO_ENDPOINT, HttpMethod.GET, requestEntity, responseType);

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("*")
            );
        }
    }

    @DisplayName("PUT /api/v1/users/password")
    @Nested
    class UpdatePassword {

        @DisplayName("유효한 정보로 비밀번호를 수정하면, 성공 응답을 반환한다.")
        @Test
        void returnsSuccess_whenValidInfoProvided() {
            // given
            UserV1Dto.RegisterRequest registerRequest = new UserV1Dto.RegisterRequest(
                "testuser",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> registerResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(registerRequest), registerResponseType);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "Password1!");

            UserV1Dto.UpdatePasswordRequest updateRequest = new UserV1Dto.UpdatePasswordRequest("NewPassword2!");
            HttpEntity<UserV1Dto.UpdatePasswordRequest> requestEntity = new HttpEntity<>(updateRequest, headers);

            // when
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(UPDATE_PASSWORD_ENDPOINT, HttpMethod.PUT, requestEntity, responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenCurrentPasswordNotMatch() {
            // given
            UserV1Dto.RegisterRequest registerRequest = new UserV1Dto.RegisterRequest(
                "testuser",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> registerResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(registerRequest), registerResponseType);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "WrongPassword1!");

            UserV1Dto.UpdatePasswordRequest updateRequest = new UserV1Dto.UpdatePasswordRequest("NewPassword2!");
            HttpEntity<UserV1Dto.UpdatePasswordRequest> requestEntity = new HttpEntity<>(updateRequest, headers);

            // when
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(UPDATE_PASSWORD_ENDPOINT, HttpMethod.PUT, requestEntity, responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 동일하면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenNewPasswordSameAsCurrent() {
            // given
            UserV1Dto.RegisterRequest registerRequest = new UserV1Dto.RegisterRequest(
                "testuser",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> registerResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(registerRequest), registerResponseType);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "Password1!");

            UserV1Dto.UpdatePasswordRequest updateRequest = new UserV1Dto.UpdatePasswordRequest("Password1!");
            HttpEntity<UserV1Dto.UpdatePasswordRequest> requestEntity = new HttpEntity<>(updateRequest, headers);

            // when
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(UPDATE_PASSWORD_ENDPOINT, HttpMethod.PUT, requestEntity, responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 8자 미만이면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenNewPasswordTooShort() {
            // given
            UserV1Dto.RegisterRequest registerRequest = new UserV1Dto.RegisterRequest(
                "testuser",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> registerResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(registerRequest), registerResponseType);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "Password1!");

            UserV1Dto.UpdatePasswordRequest updateRequest = new UserV1Dto.UpdatePasswordRequest("Pass1!");
            HttpEntity<UserV1Dto.UpdatePasswordRequest> requestEntity = new HttpEntity<>(updateRequest, headers);

            // when
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(UPDATE_PASSWORD_ENDPOINT, HttpMethod.PUT, requestEntity, responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, 400 BAD_REQUEST 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenNewPasswordContainsBirthDate() {
            // given
            UserV1Dto.RegisterRequest registerRequest = new UserV1Dto.RegisterRequest(
                "testuser",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> registerResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(registerRequest), registerResponseType);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "Password1!");

            UserV1Dto.UpdatePasswordRequest updateRequest = new UserV1Dto.UpdatePasswordRequest("19900115Pw!");
            HttpEntity<UserV1Dto.UpdatePasswordRequest> requestEntity = new HttpEntity<>(updateRequest, headers);

            // when
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response =
                testRestTemplate.exchange(UPDATE_PASSWORD_ENDPOINT, HttpMethod.PUT, requestEntity, responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("비밀번호 수정 후 새 비밀번호로 로그인할 수 있다.")
        @Test
        void canLoginWithNewPassword_afterPasswordUpdate() {
            // given
            UserV1Dto.RegisterRequest registerRequest = new UserV1Dto.RegisterRequest(
                "testuser",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.RegisterResponse>> registerResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(REGISTER_ENDPOINT, HttpMethod.POST, createRequestEntity(registerRequest), registerResponseType);

            HttpHeaders updateHeaders = new HttpHeaders();
            updateHeaders.setContentType(MediaType.APPLICATION_JSON);
            updateHeaders.set("X-Loopers-LoginId", "testuser");
            updateHeaders.set("X-Loopers-LoginPw", "Password1!");

            UserV1Dto.UpdatePasswordRequest updateRequest = new UserV1Dto.UpdatePasswordRequest("NewPassword2!");
            HttpEntity<UserV1Dto.UpdatePasswordRequest> updateRequestEntity = new HttpEntity<>(updateRequest, updateHeaders);

            ParameterizedTypeReference<ApiResponse<Void>> updateResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(UPDATE_PASSWORD_ENDPOINT, HttpMethod.PUT, updateRequestEntity, updateResponseType);

            // when - 새 비밀번호로 로그인
            HttpHeaders loginHeaders = new HttpHeaders();
            loginHeaders.set("X-Loopers-LoginId", "testuser");
            loginHeaders.set("X-Loopers-LoginPw", "NewPassword2!");
            HttpEntity<Void> loginRequestEntity = new HttpEntity<>(loginHeaders);

            ParameterizedTypeReference<ApiResponse<UserV1Dto.UserInfoResponse>> loginResponseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.UserInfoResponse>> response =
                testRestTemplate.exchange(USER_INFO_ENDPOINT, HttpMethod.GET, loginRequestEntity, loginResponseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}
