package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import static com.loopers.interfaces.api.ApiResponse.Metadata.Result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
class UserV1ApiE2ETest {

    private static final String ENDPOINT_SIGN_UP = "/customer/v1/users/sign-up";
    private static final String ENDPOINT_MY_INFO = "/customer/v1/users/me";
    private static final String ENDPOINT_POINTS = "/customer/v1/users/me/points";

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

    @DisplayName("POST /customer/v1/users/sign-up - 회원가입")
    @Nested
    class SignUp {

        @DisplayName("gender가 누락되면, 400 Bad Request를 반환한다.")
        @Test
        void signUp_withoutGender_shouldReturnBadRequest() {
            // given
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "testuser1",
                "SecurePass1!",
                "test@example.com",
                "1990-01-15",
                null // gender 누락
            );

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.FAIL)
            );
        }

        @DisplayName("userId가 누락되면, 400 Bad Request를 반환한다.")
        @Test
        void signUp_withoutUserId_shouldReturnBadRequest() {
            // given
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                null, // userId 누락
                "SecurePass1!",
                "test@example.com",
                "1990-01-15",
                "MALE"
            );

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.FAIL)
            );
        }

        @DisplayName("유효한 회원가입 요청 시, 201 Created와 생성된 사용자 정보를 반환한다.")
        @Test
        void signUp_withValidRequest_shouldReturnCreated() {
            // given
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "testuser1",
                "SecurePass1!",
                "test@example.com",
                "1990-01-15",
                "MALE"
            );

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                () -> assertThat(response.getBody().data().userId()).isEqualTo("testuser1"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("test@example.com"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo("1990-01-15"),
                () -> assertThat(response.getBody().data().gender()).isEqualTo("MALE")
            );
        }

        @DisplayName("이미 존재하는 userId로 가입 시, 409 Conflict를 반환한다.")
        @Test
        void signUp_withExistingUserId_shouldReturnConflict() {
            // given
            UserV1Dto.SignUpRequest request = new UserV1Dto.SignUpRequest(
                "testuser1",
                "SecurePass1!",
                "test@example.com",
                "1990-01-15",
                "MALE"
            );
            testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(request), new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {});

            // when - 동일한 userId로 다시 가입 시도
            UserV1Dto.SignUpRequest duplicateRequest = new UserV1Dto.SignUpRequest(
                "testuser1",
                "DiffPass2!",
                "different@example.com",
                "1995-05-20",
                "FEMALE"
            );
            ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.SignUpResponse>> response =
                testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(duplicateRequest), responseType);

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.FAIL),
                () -> assertThat(response.getBody().meta().errorCode()).isEqualTo("Conflict")
            );
        }
    }

    @DisplayName("GET /customer/v1/users/me - 내 정보 조회")
    @Nested
    class GetMyInfo {

        @DisplayName("로그인 헤더 없이 요청하면, 401 Unauthorized를 반환한다.")
        @Test
        void getMyInfo_withoutLoginHeader_shouldReturnUnauthorized() {
            // given - 헤더 없음

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response =
                testRestTemplate.exchange(ENDPOINT_MY_INFO, HttpMethod.GET, new HttpEntity<>(null), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 사용자 ID로 조회하면, 404 Not Found를 반환한다.")
        @Test
        void getMyInfo_withNonExistentUserId_shouldReturnNotFound() {
            // given
            String nonExistentUserId = "nouser";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", nonExistentUserId);

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response =
                testRestTemplate.exchange(ENDPOINT_MY_INFO, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("유효한 요청 시, 200 OK와 마스킹된 사용자 정보를 반환한다.")
        @Test
        void getMyInfo_withValidRequest_shouldReturnMaskedUserInfo() {
            // given - 먼저 회원가입
            UserV1Dto.SignUpRequest signUpRequest = new UserV1Dto.SignUpRequest(
                "johnsmith",
                "SecurePass1!",
                "john@example.com",
                "1990-01-15",
                "MALE"
            );
            testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(signUpRequest), new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {});

            // when - 내 정보 조회
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "johnsmith");
            
            ParameterizedTypeReference<ApiResponse<UserV1Dto.MyInfoResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.MyInfoResponse>> response =
                testRestTemplate.exchange(ENDPOINT_MY_INFO, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                () -> assertThat(response.getBody().data().userId()).isEqualTo("johnsmith"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("johnsmit*"),
                () -> assertThat(response.getBody().data().email()).isEqualTo("john@example.com"),
                () -> assertThat(response.getBody().data().birthDate()).isEqualTo("1990-01-15"),
                () -> assertThat(response.getBody().data().gender()).isEqualTo("MALE")
            );
        }
    }

    @DisplayName("GET /customer/v1/users/me/points - 포인트 조회")
    @Nested
    class GetPoints {

        @DisplayName("X-USER-ID 헤더가 없으면, 400 Bad Request를 반환한다.")
        @Test
        void getPoints_withoutUserIdHeader_shouldReturnBadRequest() {
            // given
            HttpHeaders headers = new HttpHeaders();
            // X-USER-ID 헤더 누락

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.PointsResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.PointsResponse>> response =
                testRestTemplate.exchange(ENDPOINT_POINTS, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.FAIL)
            );
        }

        @DisplayName("존재하지 않는 사용자 ID로 조회하면, 404 Not Found를 반환한다.")
        @Test
        void getPoints_withNonExistentUserId_shouldReturnNotFound() {
            // given
            String nonExistentUserId = "nouser";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-USER-ID", nonExistentUserId);

            // when
            ParameterizedTypeReference<ApiResponse<UserV1Dto.PointsResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.PointsResponse>> response =
                testRestTemplate.exchange(ENDPOINT_POINTS, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("유효한 요청 시, 200 OK와 포인트 정보를 반환한다.")
        @Test
        void getPoints_withValidRequest_shouldReturnPoints() {
            // given - 먼저 회원가입
            UserV1Dto.SignUpRequest signUpRequest = new UserV1Dto.SignUpRequest(
                "testuser1",
                "SecurePass1!",
                "test@example.com",
                "1990-01-15",
                "MALE"
            );
            testRestTemplate.exchange(ENDPOINT_SIGN_UP, HttpMethod.POST, new HttpEntity<>(signUpRequest), new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {});

            // when - 포인트 조회
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-USER-ID", "testuser1");
            
            ParameterizedTypeReference<ApiResponse<UserV1Dto.PointsResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<UserV1Dto.PointsResponse>> response =
                testRestTemplate.exchange(ENDPOINT_POINTS, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // then
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                () -> assertThat(response.getBody().data().userId()).isEqualTo("testuser1"),
                () -> assertThat(response.getBody().data().points()).isEqualTo(0L)
            );
        }
    }
}
