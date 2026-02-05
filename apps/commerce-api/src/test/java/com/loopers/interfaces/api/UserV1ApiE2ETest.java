package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loopers.infrastructure.UserJpaRepository;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_SIGNUP = "/api/v1/users/signup";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public UserV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        UserJpaRepository userJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/users/signup")
    @Nested
    class Signup {

        @DisplayName("유효한 회원가입 정보를 주면, 회원가입에 성공하고 사용자 정보를 반환한다.")
        @Test
        void returnsUserInfo_whenValidSignupRequestIsProvided() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "testuser1",
                "Test1234!",
                "홍길동",
                "19900101",
                "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser1"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo("19900101"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com"),
                () -> assertThat(userJpaRepository.count()).isEqualTo(1)
            );
        }

        @DisplayName("로그인 ID가 4자 미만이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenLoginIdIsTooShort() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "abc",
                "Test1234!",
                "홍길동",
                "19900101",
                "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(userJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("로그인 ID가 12자 초과이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenLoginIdIsTooLong() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "testuser12345",
                "Test1234!",
                "홍길동",
                "19900101",
                "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(userJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("로그인 ID에 특수문자가 포함되면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenLoginIdContainsSpecialCharacters() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "test@user",
                "Test1234!",
                "홍길동",
                "19900101",
                "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(userJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("비밀번호가 8자 미만이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooShort() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "testuser1",
                "Test12!",
                "홍길동",
                "19900101",
                "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(userJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("비밀번호가 16자 초과이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooLong() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "testuser1",
                "Test1234567890123!",
                "홍길동",
                "19900101",
                "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(userJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "testuser1",
                "Test19900101!",
                "홍길동",
                "19900101",
                "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(userJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("이메일 형식이 올바르지 않으면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenEmailFormatIsInvalid() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "testuser1",
                "Test1234!",
                "홍길동",
                "19900101",
                "invalid-email"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(userJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("이미 존재하는 로그인 ID로 회원가입하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenLoginIdAlreadyExists() {
            // arrange
            UserV1Dto.SignupRequest firstRequest = new UserV1Dto.SignupRequest(
                "testuser1",
                "Test1234!",
                "홍길동",
                "19900101",
                "test1@example.com"
            );
            UserV1Dto.SignupRequest secondRequest = new UserV1Dto.SignupRequest(
                "testuser1",
                "Test5678!",
                "김철수",
                "19950505",
                "test2@example.com"
            );

            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> responseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(firstRequest), responseType);

            // act
            ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(secondRequest), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(userJpaRepository.count()).isEqualTo(1)
            );
        }

        @DisplayName("이름이 2자 미만이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenNameIsTooShort() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "testuser1",
                "Test1234!",
                "홍",
                "19900101",
                "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(userJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("이름이 10자 초과이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenNameIsTooLong() {
            // arrange
            UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
                "testuser1",
                "Test1234!",
                "홍길동김철수박영희최강",
                "19900101",
                "test@example.com"
            );

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(userJpaRepository.count()).isEqualTo(0)
            );
        }
    }

    @DisplayName("GET /api/v1/users/me")
    @Nested
    class GetMyInfo {

        private static final String ENDPOINT_MY_INFO = "/api/v1/users/me";
        private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
        private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

        @DisplayName("유효한 인증 헤더로 내 정보를 조회하면, 사용자 정보를 반환한다")
        @Test
        void returnsMyInfo_whenValidAuthenticationHeaders() {
            // arrange
            UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
                "testuser1",
                "Test1234!",
                "홍길동",
                "19900101",
                "test@example.com"
            );

            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> signupResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(signupRequest), signupResponseType);

            // act
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set(HEADER_LOGIN_ID, "testuser1");
            headers.set(HEADER_LOGIN_PW, "Test1234!");

            ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response =
                testRestTemplate.exchange(ENDPOINT_MY_INFO, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().loginId()).isEqualTo("testuser1"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("홍길*"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo("19900101"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("존재하지 않는 로그인 ID로 조회하면, 401 UNAUTHORIZED 응답을 받는다")
        @Test
        void throwsUnauthorized_whenUserNotFound() {
            // arrange
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set(HEADER_LOGIN_ID, "nonexistent");
            headers.set(HEADER_LOGIN_PW, "Test1234!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response =
                testRestTemplate.exchange(ENDPOINT_MY_INFO, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }

        @DisplayName("잘못된 비밀번호로 조회하면, 401 UNAUTHORIZED 응답을 받는다")
        @Test
        void throwsUnauthorized_whenPasswordIsIncorrect() {
            // arrange
            UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
                "testuser1",
                "Test1234!",
                "홍길동",
                "19900101",
                "test@example.com"
            );

            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>> signupResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT_SIGNUP, HttpMethod.POST, new HttpEntity<>(signupRequest), signupResponseType);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set(HEADER_LOGIN_ID, "testuser1");
            headers.set(HEADER_LOGIN_PW, "Wrong1234!");

            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response =
                testRestTemplate.exchange(ENDPOINT_MY_INFO, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED)
            );
        }

        @DisplayName("인증 헤더가 없으면, 4xx 에러 응답을 받는다")
        @Test
        void throwsError_whenAuthenticationHeadersMissing() {
            // act
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response =
                testRestTemplate.exchange(ENDPOINT_MY_INFO, HttpMethod.GET, null, responseType);

            // assert
            assertTrue(response.getStatusCode().is4xxClientError() || response.getStatusCode().is5xxServerError());
        }
    }
}
