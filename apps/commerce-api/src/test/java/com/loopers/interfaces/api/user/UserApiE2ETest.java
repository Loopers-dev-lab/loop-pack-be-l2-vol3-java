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
        void 정상_요청이면_200_OK와_loginId를_반환한다() {
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
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
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

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
