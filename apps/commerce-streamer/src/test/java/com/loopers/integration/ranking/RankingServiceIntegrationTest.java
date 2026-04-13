package com.loopers.integration.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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

    @DisplayName("일간 배치를 처리하면,")
    @Nested
    class ProcessDailyBatch {

        private final String todayKey = "ranking:v1:daily:"
                + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        @DisplayName("View 이벤트가 Redis ZSET에 점수로 반영된다.")
        @Test
        void incrementsScoreForViewEvents() {
            List<RankingEvent> events = List.of(
                    new RankingEvent.View("e1", 1L),
                    new RankingEvent.View("e2", 1L),
                    new RankingEvent.View("e3", 2L));

            rankingService.processDailyBatch(events);

            assertAll(
                    () -> assertThat(redisTemplate.opsForZSet().score(todayKey, "1"))
                            .isCloseTo(0.2, offset(0.001)),
                    () -> assertThat(redisTemplate.opsForZSet().score(todayKey, "2"))
                            .isCloseTo(0.1, offset(0.001))
            );
        }

        @DisplayName("혼합 이벤트(View + Like + Order)가 상품별로 합산되어 반영된다.")
        @Test
        void aggregatesMixedEventsPerProduct() {
            List<RankingEvent> events = List.of(
                    new RankingEvent.View("e1", 1L),
                    new RankingEvent.Like("e2", 1L, true),
                    new RankingEvent.Order("e3", List.of(
                            new RankingEvent.Order.OrderItem(1L, 50000L, 1L),
                            new RankingEvent.Order.OrderItem(2L, 30000L, 2L))),
                    new RankingEvent.View("e4", 2L));

            rankingService.processDailyBatch(events);

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
            rankingService.processDailyBatch(List.of(new RankingEvent.View("e1", 1L)));
            rankingService.processDailyBatch(List.of(new RankingEvent.View("e2", 1L)));

            assertThat(redisTemplate.opsForZSet().score(todayKey, "1"))
                    .isCloseTo(0.2, offset(0.001));
        }
    }

    @DisplayName("시간 단위 배치를 처리하면,")
    @Nested
    class ProcessHourlyBatch {

        private final String currentHourKey = "ranking:v1:hourly:"
                + LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
                        .format(DateTimeFormatter.ofPattern("yyyyMMddHH"));

        @DisplayName("View 이벤트가 현재 시간 hourly 키에 반영된다.")
        @Test
        void incrementsScoreToCurrentHourKey() {
            List<RankingEvent> events = List.of(
                    new RankingEvent.View("e1", 1L),
                    new RankingEvent.View("e2", 1L),
                    new RankingEvent.View("e3", 2L));

            rankingService.processHourlyBatch(events);

            assertAll(
                    () -> assertThat(redisTemplate.opsForZSet().score(currentHourKey, "1"))
                            .isCloseTo(0.2, offset(0.001)),
                    () -> assertThat(redisTemplate.opsForZSet().score(currentHourKey, "2"))
                            .isCloseTo(0.1, offset(0.001))
            );
        }

        @DisplayName("혼합 이벤트가 상품별로 합산되어 hourly 키에 반영된다.")
        @Test
        void aggregatesMixedEventsToHourlyKey() {
            List<RankingEvent> events = List.of(
                    new RankingEvent.View("e1", 1L),
                    new RankingEvent.Like("e2", 1L, true),
                    new RankingEvent.Order("e3", List.of(
                            new RankingEvent.Order.OrderItem(1L, 50000L, 1L),
                            new RankingEvent.Order.OrderItem(2L, 30000L, 2L))),
                    new RankingEvent.View("e4", 2L));

            rankingService.processHourlyBatch(events);

            assertAll(
                    () -> assertThat(redisTemplate.opsForZSet().score(currentHourKey, "1"))
                            .isCloseTo(3.59, offset(0.02)),
                    () -> assertThat(redisTemplate.opsForZSet().score(currentHourKey, "2"))
                            .isCloseTo(3.45, offset(0.02))
            );
        }

        @DisplayName("여러 배치를 순차 처리하면 점수가 누적된다.")
        @Test
        void accumulatesScoresAcrossBatches() {
            rankingService.processHourlyBatch(List.of(new RankingEvent.View("e1", 1L)));
            rankingService.processHourlyBatch(List.of(new RankingEvent.View("e2", 1L)));

            assertThat(redisTemplate.opsForZSet().score(currentHourKey, "1"))
                    .isCloseTo(0.2, offset(0.001));
        }
    }
}
