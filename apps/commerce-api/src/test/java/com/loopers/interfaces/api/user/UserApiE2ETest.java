package com.loopers.interfaces.api.user;

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
import org.springframework.http.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserApiE2ETest {

    private static final String ENDPOINT = "/api/users";

    @Autowired
    TestRestTemplate testRestTemplate;

    @Autowired
    DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/users")
    @Nested
    class Signup {

        @Test
        void 정상_요청이면_201_CREATED와_loginId를_반환한다() {
            // Arrange
            UserDto.SignupRequest request = new UserDto.SignupRequest(
                    "loopers123", "loopers123!@", "루퍼스",
                    LocalDate.of(1996, 11, 22), "test@loopers.im"
            );

            // Act
            ResponseEntity<ApiResponse<UserDto.SignupResponse>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, jsonHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo("loopers123")
            );
        }

        @Test
        void 중복된_로그인ID면_409_CONFLICT를_반환한다() {
            // Arrange
            UserDto.SignupRequest first = new UserDto.SignupRequest(
                    "loopers123", "loopers123!@", "루퍼스",
                    LocalDate.of(1996, 11, 22), "test@loopers.im"
            );
            testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(first, jsonHeaders()),
                    new ParameterizedTypeReference<ApiResponse<UserDto.SignupResponse>>() {}
            );

            UserDto.SignupRequest duplicate = new UserDto.SignupRequest(
                    "loopers123", "otherPass123!", "다른이름",
                    LocalDate.of(2000, 1, 1), "other@loopers.im"
            );

            // Act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(duplicate, jsonHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @Test
        void 잘못된_입력값이면_400_BAD_REQUEST를_반환한다() {
            // Arrange
            UserDto.SignupRequest request = new UserDto.SignupRequest(
                    "ab", "loopers123!@", "루퍼스",
                    LocalDate.of(1996, 11, 22), "test@loopers.im"
            );

            // Act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, jsonHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @DisplayName("GET /api/users/me")
    @Nested
    class GetMyInfo {

        @Test
        void 정상_인증이면_200_OK와_유저정보를_반환한다() {
            // Arrange
            UserDto.SignupRequest signupRequest = new UserDto.SignupRequest(
                    "loopers123", "loopers123!@", "루퍼스",
                    LocalDate.of(1996, 11, 22), "test@loopers.im"
            );
            testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(signupRequest, jsonHeaders()),
                    new ParameterizedTypeReference<ApiResponse<UserDto.SignupResponse>>() {}
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "loopers123");
            headers.set("X-Loopers-LoginPw", "loopers123!@");

            // Act
            ResponseEntity<ApiResponse<UserDto.MyInfoResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "/me", HttpMethod.GET, new HttpEntity<>(null, headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo("loopers123"),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("루퍼*"),
                    () -> assertThat(response.getBody().data().birthDate()).isEqualTo(LocalDate.of(1996, 11, 22)),
                    () -> assertThat(response.getBody().data().email()).isEqualTo("test@loopers.im")
            );
        }

        @Test
        void 존재하지_않는_loginId면_404_NOT_FOUND를_반환한다() {
            // Arrange
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "nonexist12");
            headers.set("X-Loopers-LoginPw", "loopers123!@");

            // Act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/me", HttpMethod.GET, new HttpEntity<>(null, headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 비밀번호가_불일치하면_401_UNAUTHORIZED를_반환한다() {
            // Arrange
            UserDto.SignupRequest signupRequest = new UserDto.SignupRequest(
                    "loopers123", "loopers123!@", "루퍼스",
                    LocalDate.of(1996, 11, 22), "test@loopers.im"
            );
            testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(signupRequest, jsonHeaders()),
                    new ParameterizedTypeReference<ApiResponse<UserDto.SignupResponse>>() {}
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "loopers123");
            headers.set("X-Loopers-LoginPw", "wrongPass123!");

            // Act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/me", HttpMethod.GET, new HttpEntity<>(null, headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PATCH /api/users/me/password")
    @Nested
    class ChangePassword {

        @Test
        void 정상_변경이면_200_OK를_반환한다() {
            // Arrange - 회원가입
            UserDto.SignupRequest signupRequest = new UserDto.SignupRequest(
                    "loopers123", "loopers123!@", "루퍼스",
                    LocalDate.of(1996, 11, 22), "test@loopers.im"
            );
            testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(signupRequest, jsonHeaders()),
                    new ParameterizedTypeReference<ApiResponse<UserDto.SignupResponse>>() {}
            );

            HttpHeaders headers = jsonHeaders();
            headers.set("X-Loopers-LoginId", "loopers123");
            headers.set("X-Loopers-LoginPw", "loopers123!@");
            UserDto.ChangePasswordRequest request = new UserDto.ChangePasswordRequest("newPass1234!");

            // Act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/me/password", HttpMethod.PATCH, new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 변경_후_새_비밀번호로_인증할_수_있다() {
            // Arrange - 회원가입 후 비밀번호 변경
            UserDto.SignupRequest signupRequest = new UserDto.SignupRequest(
                    "loopers123", "loopers123!@", "루퍼스",
                    LocalDate.of(1996, 11, 22), "test@loopers.im"
            );
            testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST, new HttpEntity<>(signupRequest, jsonHeaders()),
                    new ParameterizedTypeReference<ApiResponse<UserDto.SignupResponse>>() {}
            );

            HttpHeaders changeHeaders = jsonHeaders();
            changeHeaders.set("X-Loopers-LoginId", "loopers123");
            changeHeaders.set("X-Loopers-LoginPw", "loopers123!@");
            testRestTemplate.exchange(
                    ENDPOINT + "/me/password", HttpMethod.PATCH,
                    new HttpEntity<>(new UserDto.ChangePasswordRequest("newPass1234!"), changeHeaders),
                    new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );

            // Act - 새 비밀번호로 내 정보 조회
            HttpHeaders newHeaders = new HttpHeaders();
            newHeaders.set("X-Loopers-LoginId", "loopers123");
            newHeaders.set("X-Loopers-LoginPw", "newPass1234!");

            ResponseEntity<ApiResponse<UserDto.MyInfoResponse>> response = testRestTemplate.exchange(
                    ENDPOINT + "/me", HttpMethod.GET, new HttpEntity<>(null, newHeaders),
                    new ParameterizedTypeReference<>() {}
            );

            // Assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().loginId()).isEqualTo("loopers123")
            );
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
