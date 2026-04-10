package com.loopers.application.ranking;

import com.loopers.application.metrics.CatalogEventHandler;
import com.loopers.application.metrics.OrderEventHandler;
import com.loopers.config.redis.RankingKeys;
import com.loopers.interfaces.consumer.OutboxMessage;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 랭킹 파이프라인 통합 테스트.
 *
 * CatalogEventHandler / OrderEventHandler → RankingSyncScheduler → Redis ZSET 흐름을
 * 한 번에 검증한다.
 *
 * 각 컴포넌트 단위 테스트:
 *   - CatalogEventHandlerIntegrationTest: 이벤트 → ranking_metrics DB 반영
 *   - RankingSyncSchedulerIntegrationTest: DB dirty → ZSET 반영
 * 이 테스트는 두 단계를 이어 "이벤트 → ZSET" 전체 흐름을 검증한다.
 */
@SpringBootTest
class RankingPipelineIntegrationTest {

    private static final Long PRODUCT_A = 100L;
    private static final Long PRODUCT_B = 200L;
    private static final int TEST_HOUR = 10;

    @Autowired
    private CatalogEventHandler catalogEventHandler;

    @Autowired
    private OrderEventHandler orderEventHandler;

    @Autowired
    private RankingSyncScheduler rankingSyncScheduler;

    @Autowired
    private RankingWeightInitializer rankingWeightInitializer;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> masterRedisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private LocalDate today;
    private String dailyKey;

    @BeforeEach
    void setUp() throws Exception {
        today = LocalDate.now();
        dailyKey = RankingKeys.dailyKey(today);
        rankingWeightInitializer.run(null);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        masterRedisTemplate.delete(dailyKey);
    }

    @DisplayName("catalog 이벤트 → ZSET 반영")
    @Nested
    class CatalogEventToZSet {

        @Test
        @DisplayName("LIKE_CREATED 이벤트 처리 후 스케줄러 실행 시 ZSET에 score가 반영된다")
        void likeCreatedEvent_reflectedInZSet() {
            // arrange
            OutboxMessage message = new OutboxMessage(
                    1L, "PRODUCT", PRODUCT_A, "LIKE_CREATED",
                    "{\"productId\": " + PRODUCT_A + ", \"userId\": 1}");

            // act
            catalogEventHandler.handle(message);
            rankingSyncScheduler.sync();

            // assert
            Double score = masterRedisTemplate.opsForZSet().score(dailyKey, String.valueOf(PRODUCT_A));
            assertThat(score).isNotNull().isGreaterThan(0);
        }

        @Test
        @DisplayName("PRODUCT_VIEWED 이벤트 처리 후 스케줄러 실행 시 ZSET에 score가 반영된다")
        void productViewedEvent_reflectedInZSet() {
            // arrange
            OutboxMessage message = new OutboxMessage(
                    1L, "PRODUCT", PRODUCT_A, "PRODUCT_VIEWED",
                    "{\"productId\": " + PRODUCT_A + ", \"userId\": 1}");

            // act
            catalogEventHandler.handle(message);
            rankingSyncScheduler.sync();

            // assert
            Double score = masterRedisTemplate.opsForZSet().score(dailyKey, String.valueOf(PRODUCT_A));
            assertThat(score).isNotNull().isGreaterThan(0);
        }
    }

    @DisplayName("order 이벤트 → ZSET 반영")
    @Nested
    class OrderEventToZSet {

        @Test
        @DisplayName("ORDER_CREATED 이벤트 처리 후 스케줄러 실행 시 ZSET에 score가 반영된다")
        void orderCreatedEvent_reflectedInZSet() {
            // arrange
            String payload = """
                    {
                      "orderId": 1, "userId": 1, "totalAmount": 29900,
                      "items": [{"productId": %d, "productName": "상품A", "price": 29900, "quantity": 1}]
                    }
                    """.formatted(PRODUCT_A);
            OutboxMessage message = new OutboxMessage(1L, "ORDER", 1L, "ORDER_CREATED", payload);

            // act
            orderEventHandler.handle(message);
            rankingSyncScheduler.sync();

            // assert
            Double score = masterRedisTemplate.opsForZSet().score(dailyKey, String.valueOf(PRODUCT_A));
            assertThat(score).isNotNull().isGreaterThan(0);
        }
    }

    @DisplayName("이벤트 누적 → 랭킹 순서 반영")
    @Nested
    class EventAccumulationToRankingOrder {

        @Test
        @DisplayName("더 많은 좋아요를 받은 상품이 더 높은 순위로 반영된다")
        void moreEventsResultInHigherRank() {
            // arrange — PRODUCT_A: 좋아요 3건, PRODUCT_B: 좋아요 1건
            catalogEventHandler.handle(likeCreated(1L, PRODUCT_A));
            catalogEventHandler.handle(likeCreated(2L, PRODUCT_A));
            catalogEventHandler.handle(likeCreated(3L, PRODUCT_A));
            catalogEventHandler.handle(likeCreated(4L, PRODUCT_B));

            // act
            rankingSyncScheduler.sync();

            // assert
            Double scoreA = masterRedisTemplate.opsForZSet().score(dailyKey, String.valueOf(PRODUCT_A));
            Double scoreB = masterRedisTemplate.opsForZSet().score(dailyKey, String.valueOf(PRODUCT_B));
            Long rankA = masterRedisTemplate.opsForZSet().reverseRank(dailyKey, String.valueOf(PRODUCT_A));
            assertAll(
                    () -> assertThat(scoreA).isGreaterThan(scoreB),
                    () -> assertThat(rankA).isEqualTo(0L)  // 0-based, 1위
            );
        }
    }

    private OutboxMessage likeCreated(Long eventId, Long productId) {
        return new OutboxMessage(
                eventId, "PRODUCT", productId, "LIKE_CREATED",
                "{\"productId\": " + productId + ", \"userId\": 1}");
    }
}
