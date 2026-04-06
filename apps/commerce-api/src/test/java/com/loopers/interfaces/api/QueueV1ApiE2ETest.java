package com.loopers.interfaces.api;

import com.loopers.application.queue.QueueProperties;
import com.loopers.application.queue.QueueStatus;
import com.loopers.config.TestRedisConfiguration;
import com.loopers.interfaces.api.queue.QueueV1Dto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestRedisConfiguration.class)
@DisplayName("Queue V1 API E2E 테스트")
class QueueV1ApiE2ETest {

    private static final String ENTER_ENDPOINT = "/api/v1/queue/enter";
    private static final String POSITION_ENDPOINT = "/api/v1/queue/position";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private QueueProperties queueProperties;

    @BeforeEach
    void setUp() {
        cleanUpRedisKeys();
    }

    @AfterEach
    void tearDown() {
        cleanUpRedisKeys();
    }

    private void cleanUpRedisKeys() {
        Set<String> queueKeys = redisTemplate.keys(queueProperties.redisKey() + "*");
        if (queueKeys != null && !queueKeys.isEmpty()) {
            redisTemplate.delete(queueKeys);
        }
        Set<String> tokenKeys = redisTemplate.keys(queueProperties.tokenPrefix() + "*");
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            redisTemplate.delete(tokenKeys);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/queue/enter")
    class Enter {

        @Test
        @DisplayName("유저가 대기열에 진입하면 순번 정보를 반환한다")
        void 대기열_진입_성공() {
            // Arrange
            QueueV1Dto.QueueEnterRequest request = new QueueV1Dto.QueueEnterRequest(1L);

            // Act
            ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueueEnterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<QueueV1Dto.QueueEnterResponse>> response =
                testRestTemplate.exchange(ENTER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // Assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().userId()).isEqualTo(1L),
                () -> assertThat(response.getBody().data().position()).isEqualTo(1),
                () -> assertThat(response.getBody().data().status()).isEqualTo(QueueStatus.WAITING.name())
            );
        }

        @Test
        @DisplayName("userId가 없으면 400 Bad Request를 반환한다")
        void userId_없으면_400() {
            // Arrange
            QueueV1Dto.QueueEnterRequest request = new QueueV1Dto.QueueEnterRequest(null);

            // Act
            ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueueEnterResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<QueueV1Dto.QueueEnterResponse>> response =
                testRestTemplate.exchange(ENTER_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // Assert
            assertTrue(response.getStatusCode().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/queue/position")
    class Position {

        @Test
        @DisplayName("대기열에 있는 유저의 순번을 조회한다")
        void 순번_조회_성공() {
            // Arrange: 먼저 대기열에 진입
            testRestTemplate.postForEntity(ENTER_ENDPOINT, new QueueV1Dto.QueueEnterRequest(1L), Object.class);
            testRestTemplate.postForEntity(ENTER_ENDPOINT, new QueueV1Dto.QueueEnterRequest(2L), Object.class);

            // Act
            ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueuePositionResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<QueueV1Dto.QueuePositionResponse>> response =
                testRestTemplate.exchange(POSITION_ENDPOINT + "?userId=2", HttpMethod.GET, null, responseType);

            // Assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().userId()).isEqualTo(2L),
                () -> assertThat(response.getBody().data().position()).isEqualTo(2),
                () -> assertThat(response.getBody().data().totalWaiting()).isEqualTo(2),
                () -> assertThat(response.getBody().data().status()).isEqualTo(QueueStatus.WAITING.name())
            );
        }

        @Test
        @DisplayName("대기열에 없는 유저를 조회하면 NOT_IN_QUEUE 상태를 반환한다")
        void 대기열에_없는_유저_조회() {
            // Act
            ParameterizedTypeReference<ApiResponse<QueueV1Dto.QueuePositionResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<QueueV1Dto.QueuePositionResponse>> response =
                testRestTemplate.exchange(POSITION_ENDPOINT + "?userId=999", HttpMethod.GET, null, responseType);

            // Assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().status()).isEqualTo(QueueStatus.NOT_IN_QUEUE.name())
            );
        }
    }
}