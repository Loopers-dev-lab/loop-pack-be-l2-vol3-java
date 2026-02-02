package com.loopers.interfaces.api.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

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

import com.loopers.domain.user.UserName;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.v1.UserV1Dto;
import com.loopers.interfaces.api.user.v1.UserV1Dto.MeResponse;
import com.loopers.interfaces.api.user.v1.UserV1Dto.SignUpResponse;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String SIGNUP_ENDPOINT = "/api/v1/users/signup";

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

    @DisplayName("POST /api/v1/users/signup")
    @Nested
    class SignUp {

        @DisplayName("유효한 회원 정보를 입력하면, 회원가입에 성공한다.")
        @Test
        void signUp_success_whenValidInput() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "testuser1",
                    "Password1!",
                    "홍길동",
                    "1990-01-15",
                    "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                    testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo(request.loginId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(new UserName(request.name()).masked()),
                    () -> assertThat(response.getBody().data().email()).isEqualTo(request.email())
            );
        }

        @DisplayName("이미 가입된 로그인 ID로 가입하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void signUp_badRequest_whenDuplicateLoginId() {
            // arrange
            UserV1Dto.SignUpRequest firstRequest = new UserV1Dto.SignUpRequest(
                    "testuser1",
                    "Password1!",
                    "홍길동",
                    "1990-01-15",
                    "test@example.com"
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(firstRequest), responseType);

            UserV1Dto.SignUpRequest duplicateRequest = new UserV1Dto.SignUpRequest(
                    "testuser1",
                    "Password2@",
                    "김철수",
                    "1985-05-20",
                    "other@example.com"
            );

            // act
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                    testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(duplicateRequest), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("유효하지 않은 로그인 ID 형식이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void signUp_badRequest_whenInvalidLoginIdFormat() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "test-user!",
                    "Password1!",
                    "홍길동",
                    "1990-01-15",
                    "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                    testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("유효하지 않은 이메일 형식이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void signUp_badRequest_whenInvalidEmailFormat() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "testuser1",
                    "Password1!",
                    "홍길동",
                    "1990-01-15",
                    "invalid-email"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                    testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("유효하지 않은 비밀번호 형식이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void signUp_badRequest_whenInvalidPasswordFormat() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "testuser1",
                    "short",
                    "홍길동",
                    "1990-01-15",
                    "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                    testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void signUp_badRequest_whenPasswordContainsBirthDate() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "testuser1",
                    "Pass19900115!",
                    "홍길동",
                    "1990-01-15",
                    "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                    testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("이름이 빈 값이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void signUp_badRequest_whenNameIsEmpty() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "testuser1",
                    "Password1!",
                    "",
                    "1990-01-15",
                    "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                    testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("유효하지 않은 생년월일 형식이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void signUp_badRequest_whenInvalidBirthDateFormat() {
            // arrange
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                    "testuser1",
                    "Password1!",
                    "홍길동",
                    "19900115",
                    "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                    testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMe {

        private static final String ME_ENDPOINT = "/api/v1/users/me";

        @DisplayName("인증된 사용자 정보를 조회하면, 마스킹된 이름과 함께 사용자 정보를 반환한다.")
        @Test
        void getMe_returnsMaskedName_whenAuthenticated() {
            // arrange
            UserV1Dto.SignUpRequest signUpRequest = new UserV1Dto.SignUpRequest(
                    "testuser1",
                    "Password1!",
                    "홍길동",
                    "1990-01-15",
                    "test@example.com"
            );
            ParameterizedTypeReference<ApiResponse<SignUpResponse>> signUpResponseType = new ParameterizedTypeReference<>() {
            };
            testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(signUpRequest), signUpResponseType);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", signUpRequest.loginId());
            headers.set("X-Loopers-LoginPw", signUpRequest.password());

            // act
            ParameterizedTypeReference<ApiResponse<MeResponse>> responseType = new ParameterizedTypeReference<>() {
            };
            ResponseEntity<ApiResponse<MeResponse>> response =
                    testRestTemplate.exchange(ME_ENDPOINT, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo(signUpRequest.loginId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(new UserName(signUpRequest.name()).masked()),
                    () -> assertThat(response.getBody().data().birthDate()).isEqualTo(signUpRequest.birthDate()),
                    () -> assertThat(response.getBody().data().email()).isEqualTo(signUpRequest.email())
            );
        }
    }
}
