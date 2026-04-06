package com.loopers.application.queue;

import com.loopers.config.TestRedisConfiguration;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.queue.QueueV1Dto;
import com.loopers.utils.DatabaseCleanUp;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestRedisConfiguration.class)
@DisplayName("Queue 통합 테스트")
class QueueIntegrationTest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private QueueService queueService;

    @Autowired
    private QueueScheduler queueScheduler;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private QueueProperties queueProperties;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        cleanUpRedisKeys();

        Brand brand = brandRepository.save(Brand.create("Test Brand", "Test", null));
        testProduct = productRepository.save(Product.create(
            brand.getId(), "Test Product", "Test", new Money(10000), new Stock(1000), null
        ));
    }

    @AfterEach
    void tearDown() {
        cleanUpRedisKeys();
        databaseCleanUp.truncateAllTables();
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
    @DisplayName("전체 대기열 흐름")
    class FullFlow {

        @Test
        @DisplayName("대기열 진입 → 스케줄러 → 토큰 발급 → 주문 → 토큰 삭제 전체 흐름")
        void 전체_대기열_흐름_통합_테스트() {
            // 1. 대기열 진입 — 5명의 유저가 순서대로 진입
            for (int i = 1; i <= 5; i++) {
                QueueResult result = queueService.enter((long) i);
                assertThat(result.position()).isEqualTo(i);
                assertThat(result.status()).isEqualTo(QueueStatus.WAITING);
            }

            // 2. 순번 조회 — userId=3의 순번이 3인지 확인
            QueuePositionResult positionResult = queueService.getPosition(3L);
            assertThat(positionResult.position()).isEqualTo(3);
            assertThat(positionResult.status()).isEqualTo(QueueStatus.WAITING);

            // 3. 스케줄러 수동 실행
            queueScheduler.processQueue();

            // 4. 토큰 발급된 유저의 순번 조회
            QueuePositionResult tokenResult = queueService.getPosition(1L);
            assertThat(tokenResult.position()).isEqualTo(0);
            assertThat(tokenResult.status()).isEqualTo(QueueStatus.TOKEN_ISSUED);
            assertThat(tokenResult.token()).isNotNull();

            // 5. 토큰으로 주문 API 호출
            String token = tokenResult.token();
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", "1");
            headers.set("X-Entry-Token", token);
            headers.set("Content-Type", "application/json");

            OrderV1Dto.PlaceOrderRequestDto orderRequest = new OrderV1Dto.PlaceOrderRequestDto(
                List.of(new OrderV1Dto.OrderItemRequestDto(testProduct.getId(), 1))
            );
            HttpEntity<OrderV1Dto.PlaceOrderRequestDto> orderEntity = new HttpEntity<>(orderRequest, headers);

            ResponseEntity<String> orderResponse =
                testRestTemplate.exchange("/api/v1/orders", HttpMethod.POST, orderEntity, String.class);
            assertThat(orderResponse.getStatusCode().is2xxSuccessful()).isTrue();

            // 6. 사용된 토큰으로 재주문 시도
            ResponseEntity<String> reOrderResponse =
                testRestTemplate.exchange("/api/v1/orders", HttpMethod.POST, orderEntity, String.class);
            assertThat(reOrderResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            // 7. 토큰 없이 주문 시도
            HttpHeaders noTokenHeaders = new HttpHeaders();
            noTokenHeaders.set("X-User-Id", "2");
            noTokenHeaders.set("Content-Type", "application/json");
            HttpEntity<OrderV1Dto.PlaceOrderRequestDto> noTokenEntity = new HttpEntity<>(orderRequest, noTokenHeaders);

            ResponseEntity<String> noTokenResponse =
                testRestTemplate.exchange("/api/v1/orders", HttpMethod.POST, noTokenEntity, String.class);
            assertThat(noTokenResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("동시성 테스트")
    class Concurrency {

        @Test
        @DisplayName("동시에 1000명이 대기열에 진입해도 순번이 정확하다")
        void 동시_1000명_진입_순번_정확() throws InterruptedException {
            // given
            int userCount = 1000;
            ExecutorService executor = Executors.newFixedThreadPool(50);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(userCount);
            AtomicInteger successCount = new AtomicInteger();

            // when — 1000명 동시 진입
            for (int i = 1; i <= userCount; i++) {
                final long userId = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        queueService.enter(userId);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        // ignore
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            // then
            // 1. 대기열에 정확히 1000명이 있는지 (ZCARD == 1000)
            Long queueSize = redisTemplate.opsForZSet().size(queueProperties.redisKey());
            assertThat(queueSize).isEqualTo(1000);
            assertThat(successCount.get()).isEqualTo(1000);

            // 2. 각 유저의 순번이 1~1000 범위인지
            Set<Integer> positions = new HashSet<>();
            for (int i = 1; i <= userCount; i++) {
                QueuePositionResult result = queueService.getPosition((long) i);
                assertThat(result.position()).isBetween(1, 1000);
                positions.add(result.position());
            }

            // 3. 순번에 중복이 없는지
            assertThat(positions).hasSize(1000);
        }

        @Test
        @DisplayName("스케줄러가 배치 크기만큼만 처리하고 나머지는 대기한다")
        void 스케줄러_배치_크기_제한_및_선착순_보장() {
            // given — 대기열에 100명
            for (int i = 1; i <= 100; i++) {
                queueService.enter((long) i);
            }

            int batchSize = Math.max(1, queueProperties.throughputPerSecond() / 10);

            // when — 스케줄러 1회 실행
            queueScheduler.processQueue();

            // then
            // 1. 토큰 발급된 유저 수 == batchSize
            int tokenCount = 0;
            for (int i = 1; i <= 100; i++) {
                if (tokenService.hasToken((long) i)) {
                    tokenCount++;
                }
            }
            assertThat(tokenCount).isEqualTo(batchSize);

            // 2. 대기열 잔여 인원 == 100 - batchSize
            Long remainingSize = redisTemplate.opsForZSet().size(queueProperties.redisKey());
            assertThat(remainingSize).isEqualTo(100 - batchSize);

            // 3. 토큰 발급된 유저가 1~batchSize 범위인지 (선착순 보장)
            for (int i = 1; i <= batchSize; i++) {
                assertThat(tokenService.hasToken((long) i)).isTrue();
            }
        }
    }

    @Nested
    @DisplayName("대기열 이탈")
    class Leave {

        @Test
        @DisplayName("대기열에서 이탈하면 순번 조회 시 NOT_IN_QUEUE 반환")
        void 대기열_이탈_성공() {
            // Arrange
            queueService.enter(1L);
            assertThat(queueService.getPosition(1L).status()).isEqualTo(QueueStatus.WAITING);

            // Act
            QueueLeaveResult result = queueService.leave(1L);

            // Assert
            assertThat(result.status()).isEqualTo(QueueLeaveStatus.LEFT);
            assertThat(queueService.getPosition(1L).status()).isEqualTo(QueueStatus.NOT_IN_QUEUE);
        }

        @Test
        @DisplayName("토큰이 발급된 상태에서 이탈하면 토큰도 삭제된다")
        void 토큰_발급_후_이탈() {
            // Arrange
            queueService.enter(1L);
            queueScheduler.processQueue();
            assertThat(tokenService.hasToken(1L)).isTrue();

            // Act
            QueueLeaveResult result = queueService.leave(1L);

            // Assert
            assertThat(result.status()).isEqualTo(QueueLeaveStatus.LEFT);
            assertThat(tokenService.hasToken(1L)).isFalse();
        }

        @Test
        @DisplayName("대기열에 없는 유저가 이탈 시도하면 NOT_IN_QUEUE 반환")
        void 대기열에_없는_유저_이탈() {
            // Act
            QueueLeaveResult result = queueService.leave(999L);

            // Assert
            assertThat(result.status()).isEqualTo(QueueLeaveStatus.NOT_IN_QUEUE);
        }
    }

    @Nested
    @DisplayName("예상 대기 시간 계산")
    class EstimatedWaitTime {

        @Test
        @DisplayName("예상 대기 시간에 20% 안전 마진이 적용된다")
        void 안전_마진_적용() {
            // Arrange
            int throughput = queueProperties.throughputPerSecond();

            // 175명이 대기열에 있으면 기본 대기 시간은 1초
            // 안전 마진 20% 적용 시 1.2초 -> 2초 (올림)
            for (int i = 1; i <= throughput; i++) {
                queueService.enter((long) i);
            }

            // Act
            QueuePositionResult result = queueService.getPosition((long) throughput);

            // Assert
            // position = throughput, baseEstimate = 1초, 마진 적용 = 1.2초 -> 2초
            int expectedBase = (int) Math.ceil((double) throughput / throughput);
            int expectedWithMargin = (int) Math.ceil(expectedBase * 1.2);
            assertThat(result.estimatedWaitSeconds()).isEqualTo(expectedWithMargin);
        }
    }
}