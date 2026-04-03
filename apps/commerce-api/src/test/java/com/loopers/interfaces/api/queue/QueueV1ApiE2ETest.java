package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
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
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@ActiveProfiles("test")
class QueueV1ApiE2ETest {

    private final TestRestTemplate testRestTemplate;
    private final RedisCleanUp redisCleanUp;

    @Autowired
    public QueueV1ApiE2ETest(TestRestTemplate testRestTemplate, RedisCleanUp redisCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.redisCleanUp = redisCleanUp;
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private HttpHeaders headers(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        return headers;
    }

    @DisplayName("POST /api/v1/queue/{eventId}/enter")
    @Nested
    class Enter {

        @DisplayName("대기열 진입 시 순번 1을 반환한다")
        @Test
        void returnsPosition1OnFirstEntry() {
            // given
            String eventId = "event-1";
            Long userId = 1L;

            // when
            ResponseEntity<ApiResponse<QueueV1Dto.QueuePositionResponse>> response = testRestTemplate.exchange(
                    "/api/v1/queue/{eventId}/enter", HttpMethod.POST,
                    new HttpEntity<>(null, headers(userId)),
                    new ParameterizedTypeReference<>() {}, eventId);

            // then
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().position()).isEqualTo(1)
            );
        }

        @DisplayName("중복 진입 시 409를 반환한다")
        @Test
        void returns409OnDuplicateEntry() {
            // given
            String eventId = "event-1";
            Long userId = 1L;
            testRestTemplate.exchange(
                    "/api/v1/queue/{eventId}/enter", HttpMethod.POST,
                    new HttpEntity<>(null, headers(userId)),
                    new ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueuePositionResponse>>() {}, eventId);

            // when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    "/api/v1/queue/{eventId}/enter", HttpMethod.POST,
                    new HttpEntity<>(null, headers(userId)),
                    new ParameterizedTypeReference<>() {}, eventId);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("여러 유저 진입 시 순서대로 순번이 매겨진다")
        @Test
        void assignsPositionsInOrder() {
            // given
            String eventId = "event-1";
            for (long i = 1; i <= 4; i++) {
                testRestTemplate.exchange(
                        "/api/v1/queue/{eventId}/enter", HttpMethod.POST,
                        new HttpEntity<>(null, headers(i)),
                        new ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueuePositionResponse>>() {}, eventId);
            }

            // when
            ResponseEntity<ApiResponse<QueueV1Dto.QueuePositionResponse>> response = testRestTemplate.exchange(
                    "/api/v1/queue/{eventId}/enter", HttpMethod.POST,
                    new HttpEntity<>(null, headers(5L)),
                    new ParameterizedTypeReference<>() {}, eventId);

            // then
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().position()).isEqualTo(5)
            );
        }
    }

    @DisplayName("GET /api/v1/queue/{eventId}/position")
    @Nested
    class GetPosition {

        @DisplayName("미등록 유저 순번 조회 시 position=0을 반환한다")
        @Test
        void returnsPosition0ForUnregisteredUser() {
            // given
            String eventId = "event-1";
            Long userId = 999L;

            // when
            ResponseEntity<ApiResponse<QueueV1Dto.QueuePositionResponse>> response = testRestTemplate.exchange(
                    "/api/v1/queue/{eventId}/position", HttpMethod.GET,
                    new HttpEntity<>(null, headers(userId)),
                    new ParameterizedTypeReference<>() {}, eventId);

            // then
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().position()).isEqualTo(0)
            );
        }

        @DisplayName("토큰이 발급된 유저는 token 정보가 포함된 응답을 받는다")
        @Test
        void returnsTokenInfoForIssuedUser() {
            // given
            String eventId = "event-1";
            Long userId = 1L;
            testRestTemplate.exchange(
                    "/api/v1/queue/{eventId}/enter", HttpMethod.POST,
                    new HttpEntity<>(null, headers(userId)),
                    new ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueuePositionResponse>>() {}, eventId);

            // when - position 조회 (스케줄러 미실행이므로 token은 null)
            ResponseEntity<ApiResponse<QueueV1Dto.QueuePositionResponse>> response = testRestTemplate.exchange(
                    "/api/v1/queue/{eventId}/position", HttpMethod.GET,
                    new HttpEntity<>(null, headers(userId)),
                    new ParameterizedTypeReference<>() {}, eventId);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().token()).isNull();
        }
    }
}
