package com.loopers.integration.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import com.loopers.application.ranking.RankingService;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingEvent;
import com.loopers.utils.RedisCleanUp;

@SpringBootTest
class RankingServiceIntegrationTest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String KEY_PREFIX = "ranking:v1:all:";

    @Autowired
    private RankingService rankingService;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("이벤트 배치를 처리하면,")
    @Nested
    class ProcessBatch {

        private final String todayKey = KEY_PREFIX + LocalDate.now().format(DATE_FORMAT);

        @DisplayName("View 이벤트가 Redis ZSET에 점수로 반영된다.")
        @Test
        void incrementsScoreForViewEvents() {
            // arrange
            List<RankingEvent> events = List.of(
                    new RankingEvent.View("e1", 1L),
                    new RankingEvent.View("e2", 1L),
                    new RankingEvent.View("e3", 2L)
            );

            // act
            rankingService.processBatch(events);

            // assert
            assertAll(
                    () -> assertThat(redisTemplate.opsForZSet().score(todayKey, "1"))
                            .isCloseTo(0.2, offset(0.001)),
                    () -> assertThat(redisTemplate.opsForZSet().score(todayKey, "2"))
                            .isCloseTo(0.1, offset(0.001))
            );
        }

        @DisplayName("Like 이벤트가 Redis ZSET에 점수로 반영된다.")
        @Test
        void incrementsScoreForLikeEvents() {
            // arrange
            List<RankingEvent> events = List.of(
                    new RankingEvent.Like("e1", 1L, true),
                    new RankingEvent.Like("e2", 1L, true),
                    new RankingEvent.Like("e3", 1L, false)
            );

            // act
            rankingService.processBatch(events);

            // assert
            assertThat(redisTemplate.opsForZSet().score(todayKey, "1"))
                    .isCloseTo(0.2, offset(0.001));
        }

        @DisplayName("Order 이벤트가 Redis ZSET에 가중치 점수로 반영된다.")
        @Test
        void incrementsScoreForOrderEvents() {
            // arrange
            List<RankingEvent> events = List.of(
                    new RankingEvent.Order("e1", List.of(
                            new RankingEvent.Order.OrderItem(1L, 50000L, 1L)
                    ))
            );

            // act
            rankingService.processBatch(events);

            // assert
            assertThat(redisTemplate.opsForZSet().score(todayKey, "1"))
                    .isCloseTo(3.29, offset(0.01));
        }

        @DisplayName("혼합 이벤트(View + Like + Order)가 상품별로 합산되어 반영된다.")
        @Test
        void aggregatesMixedEventsPerProduct() {
            // arrange
            List<RankingEvent> events = List.of(
                    new RankingEvent.View("e1", 1L),
                    new RankingEvent.Like("e2", 1L, true),
                    new RankingEvent.Order("e3", List.of(
                            new RankingEvent.Order.OrderItem(1L, 50000L, 1L),
                            new RankingEvent.Order.OrderItem(2L, 30000L, 2L)
                    )),
                    new RankingEvent.View("e4", 2L)
            );

            // act
            rankingService.processBatch(events);

            // assert
            assertAll(
                    () -> assertThat(redisTemplate.opsForZSet().score(todayKey, "1"))
                            .isCloseTo(3.59, offset(0.02)),
                    () -> assertThat(redisTemplate.opsForZSet().score(todayKey, "2"))
                            .isCloseTo(3.45, offset(0.02))
            );
        }

        @DisplayName("여러 배치를 순차 처리하면 점수가 누적된다.")
        @Test
        void accumulatesScoresAcrossBatches() {
            // arrange & act — 배치 1
            rankingService.processBatch(List.of(
                    new RankingEvent.View("e1", 1L)
            ));
            // 배치 2
            rankingService.processBatch(List.of(
                    new RankingEvent.View("e2", 1L)
            ));

            // assert
            assertThat(redisTemplate.opsForZSet().score(todayKey, "1"))
                    .isCloseTo(0.2, offset(0.001));
        }
    }
}
