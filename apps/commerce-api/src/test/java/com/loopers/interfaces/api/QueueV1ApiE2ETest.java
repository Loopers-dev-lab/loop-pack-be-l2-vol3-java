package com.loopers.interfaces.api;

import com.loopers.interfaces.api.queue.QueueV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "queue.interceptor.enabled=true")
@DisplayName("Queue API E2E 테스트")
class QueueV1ApiE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private HttpHeaders headersWithUserId(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId.toString());
        return headers;
    }

    @Nested
    @DisplayName("POST /api/v1/queue/enter — 대기열 진입")
    class Enter {

        @Test
        @DisplayName("성공: 대기열에 진입하고 순번을 반환한다")
        void enter_success() {
            // When
            ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> response = restTemplate.exchange(
                    "/api/v1/queue/enter",
                    HttpMethod.POST,
                    new HttpEntity<>(headersWithUserId(1L)),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            QueueV1Dto.EnterResponse data = response.getBody().data();
            assertThat(data.userId()).isEqualTo(1L);
            assertThat(data.position()).isGreaterThanOrEqualTo(0); // 스케줄러가 이미 처리했을 수 있음
            assertThat(data.newEntry()).isTrue();
        }

        @Test
        @DisplayName("동시에 여러 유저가 진입해도 진입에 성공한다")
        void enter_multipleUsers() {
            // When
            int successCount = 0;
            for (long i = 1; i <= 20; i++) {
                ResponseEntity<ApiResponse<QueueV1Dto.EnterResponse>> response = restTemplate.exchange(
                        "/api/v1/queue/enter",
                        HttpMethod.POST,
                        new HttpEntity<>(headersWithUserId(i)),
                        new ParameterizedTypeReference<>() {}
                );
                if (response.getStatusCode() == HttpStatus.OK && response.getBody().data().newEntry()) {
                    successCount++;
                }
            }

            // Then — 20명 모두 진입 성공
            assertThat(successCount).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/queue/position — 순번 조회")
    class Position {

        @Test
        @DisplayName("대기열에 없는 유저는 404")
        void position_notFound() {
            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api/v1/queue/position",
                    HttpMethod.GET,
                    new HttpEntity<>(headersWithUserId(999L)),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("토큰이 발급된 유저는 position 조회 시 token을 반환한다")
        void position_withToken() throws InterruptedException {
            // Given — 유저 진입
            restTemplate.exchange("/api/v1/queue/enter", HttpMethod.POST,
                    new HttpEntity<>(headersWithUserId(1L)),
                    new ParameterizedTypeReference<ApiResponse<QueueV1Dto.EnterResponse>>() {});

            // 스케줄러가 토큰을 발급할 시간 대기
            Thread.sleep(500);

            // When
            ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> response = restTemplate.exchange(
                    "/api/v1/queue/position",
                    HttpMethod.GET,
                    new HttpEntity<>(headersWithUserId(1L)),
                    new ParameterizedTypeReference<>() {}
            );

            // Then — 스케줄러가 처리했으므로 토큰이 있어야 함
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            QueueV1Dto.PositionResponse data = response.getBody().data();
            assertThat(data.token()).isNotNull();
            assertThat(data.position()).isEqualTo(0); // 대기열에서 빠진 상태
        }
    }

    @Nested
    @DisplayName("토큰 검증 — 주문 API Interceptor")
    class TokenValidation {

        @Test
        @DisplayName("토큰 없이 주문하면 403 Forbidden")
        void order_withoutToken() {
            // When
            HttpHeaders headers = headersWithUserId(1L);
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/orders",
                    HttpMethod.POST,
                    new HttpEntity<>("{\"items\":[{\"productId\":1,\"quantity\":1}]}", headers),
                    String.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }
}
