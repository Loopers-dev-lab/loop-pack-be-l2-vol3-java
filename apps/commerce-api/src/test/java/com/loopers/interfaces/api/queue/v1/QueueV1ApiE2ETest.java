package com.loopers.interfaces.api.queue.v1;

import static com.loopers.interfaces.api.queue.v1.QueueSteps.enterQueue;
import static com.loopers.interfaces.api.queue.v1.QueueSteps.getPosition;
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

        @DisplayName("이미 대기열에 진입한 사용자가 재진입하면, 400 ALREADY_IN_QUEUE 에러 응답을 받는다.")
        @Test
        void returnsBadRequest_whenAlreadyInQueue() {
            // arrange
            enterQueue(testRestTemplate, userHeaders);

            // act
            var response = enterQueue(testRestTemplate, userHeaders);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.ALREADY_IN_QUEUE);
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

    @DisplayName("GET /api/v1/queue/position")
    @Nested
    class GetPosition {

        @DisplayName("대기열에 진입한 사용자가 순번을 조회하면, 200 성공 응답과 순번 정보를 받는다.")
        @Test
        void returnsPosition_whenInQueue() {
            // arrange
            enterQueue(testRestTemplate, userHeaders);

            // act
            var response = getPosition(testRestTemplate, userHeaders);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().position()).isEqualTo(1),
                    () -> assertThat(response.getBody().data().totalWaiting()).isEqualTo(1),
                    () -> assertThat(response.getBody().data().estimatedWaitSeconds()).isEqualTo(1)
            );
        }

        @DisplayName("��러 사용자가 대기열에 있을 때, 자신의 순번과 전체 인원이 정확히 반환된다.")
        @Test
        void returnsCorrectPositionAndTotal_whenMultipleUsersInQueue() {
            // arrange — 다른 사용자 먼저 진입
            var otherRequest = new UserV1Dto.SignUpRequest(
                    "testuser2", "Password2!", "김철수", "1991-05-20", "other@example.com"
            );
            signUp(testRestTemplate, otherRequest);
            var otherHeaders = userAuthHeaders(otherRequest.loginId(), otherRequest.password());
            enterQueue(testRestTemplate, otherHeaders);

            // 현재 사용자 진입
            enterQueue(testRestTemplate, userHeaders);

            // act
            var response = getPosition(testRestTemplate, userHeaders);

            // assert
            assertAll(
                    () -> assertThat(response.getBody().data().position()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().totalWaiting()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().estimatedWaitSeconds()).isEqualTo(1)
            );
        }

        @DisplayName("대기열에 진입하지 않은 사용자가 조회하면, 404 QUEUE_NOT_ENTERED 에러 응답을 받는다.")
        @Test
        void returnsNotFound_whenNotInQueue() {
            // act
            var response = getPosition(testRestTemplate, userHeaders);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.QUEUE_NOT_ENTERED);
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenNoAuthHeader() {
            // act
            var response = getPosition(testRestTemplate, new HttpHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }
    }
}
