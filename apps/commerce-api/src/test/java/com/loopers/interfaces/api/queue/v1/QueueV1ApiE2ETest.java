package com.loopers.interfaces.api.queue.v1;

import static com.loopers.interfaces.api.queue.v1.QueueSteps.enterQueue;
import static com.loopers.interfaces.api.user.v1.UserSteps.signUp;
import static com.loopers.support.E2ETestHelper.assertErrorResponse;
import static com.loopers.support.E2ETestHelper.userAuthHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import com.loopers.interfaces.api.user.v1.UserV1Dto;
import com.loopers.support.BaseE2ETest;
import com.loopers.support.error.ErrorType;

@DisplayName("QueueV1Api E2E 테스트")
class QueueV1ApiE2ETest extends BaseE2ETest {

    private HttpHeaders userHeaders;

    @BeforeEach
    void setUp() {
        var signUpRequest = new UserV1Dto.SignUpRequest(
                "testuser1", "Password1!", "홍길동", "1990-01-15", "test@example.com"
        );
        signUp(testRestTemplate, signUpRequest);
        userHeaders = userAuthHeaders(signUpRequest.loginId(), signUpRequest.password());
    }

    @DisplayName("POST /api/v1/queue/enter")
    @Nested
    class EnterQueue {

        @DisplayName("인증된 사용자가 대기열에 진입하면, 200 성공 응답을 받는다.")
        @Test
        void returnsSuccess_whenAuthenticated() {
            // act
            var response = enterQueue(testRestTemplate, userHeaders);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().meta().errorCode()).isNull()
            );
        }

        @DisplayName("이미 대기열에 진입한 사용자가 재진입하면, 409 ALREADY_IN_QUEUE 에러 응답을 받는다.")
        @Test
        void returnsConflict_whenAlreadyInQueue() {
            // arrange
            enterQueue(testRestTemplate, userHeaders);

            // act
            var response = enterQueue(testRestTemplate, userHeaders);

            // assert
            assertErrorResponse(response, HttpStatus.CONFLICT, ErrorType.ALREADY_IN_QUEUE);
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenNoAuthHeader() {
            // act
            var response = enterQueue(testRestTemplate, new HttpHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }
    }
}
