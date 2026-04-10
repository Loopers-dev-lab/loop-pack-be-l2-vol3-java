package com.loopers.application.ranking;

import com.loopers.config.redis.RankingKeys;
import com.loopers.domain.ranking.RankingMetricsService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RankingSyncSchedulerIntegrationTest {

    private static final Long PRODUCT_A = 100L;
    private static final Long PRODUCT_B = 200L;
    private static final int TEST_HOUR = 10;

    @Autowired
    private RankingSyncScheduler rankingSyncScheduler;

    @Autowired
    private RankingMetricsService rankingMetricsService;

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
        // truncateAllTables() 이후 ranking_weight가 비어있으므로 재적재
        // RankingWeightInitializer는 존재하지 않는 eventType만 삽입하므로 멱등
        rankingWeightInitializer.run(null);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        masterRedisTemplate.delete(dailyKey);
        masterRedisTemplate.delete(RankingKeys.hourlyKey(today, TEST_HOUR));
        // weight cache 키는 삭제하지 않음 — 값이 불변이므로 stale 무해
    }

    @DisplayName("dirty 행 → Redis 동기화")
    @Nested
    class SyncDirtyRows {

        @Test
        @DisplayName("dirty=true인 행이 있으면 스케줄러 실행 후 Redis ZSET에 score가 추가된다")
        void addsScoreToZSet_whenDirtyRowExists() {
            // arrange
            rankingMetricsService.incrementViewCount(PRODUCT_A, today, TEST_HOUR);

            // act
            rankingSyncScheduler.sync();

            // assert
            Double score = masterRedisTemplate.opsForZSet().score(dailyKey, String.valueOf(PRODUCT_A));
            assertThat(score).isNotNull().isGreaterThan(0);
        }

        @Test
        @DisplayName("스케줄러 실행 후 처리된 dirty 행은 dirty=false로 전환된다")
        void clearsDirtyFlag_afterSync() {
            // arrange
            rankingMetricsService.incrementViewCount(PRODUCT_A, today, TEST_HOUR);

            // act
            rankingSyncScheduler.sync();

            // assert
            assertThat(rankingMetricsService.findDirtyEntriesGroupedByProduct(today)).isEmpty();
        }

        @Test
        @DisplayName("dirty 행이 없으면 Redis ZSET이 변경되지 않는다")
        void doesNotModifyZSet_whenNoDirtyRows() {
            // act
            rankingSyncScheduler.sync();

            // assert
            Long size = masterRedisTemplate.opsForZSet().size(dailyKey);
            assertThat(size == null || size == 0L).isTrue();
        }

        @Test
        @DisplayName("여러 상품의 dirty 행을 한 번에 모두 동기화한다")
        void syncsMultipleProducts_inOneExecution() {
            // arrange
            rankingMetricsService.incrementViewCount(PRODUCT_A, today, TEST_HOUR);
            rankingMetricsService.incrementLikeCount(PRODUCT_B, today, TEST_HOUR);

            // act
            rankingSyncScheduler.sync();

            // assert
            assertThat(masterRedisTemplate.opsForZSet().score(dailyKey, String.valueOf(PRODUCT_A))).isNotNull();
            assertThat(masterRedisTemplate.opsForZSet().score(dailyKey, String.valueOf(PRODUCT_B))).isNotNull();
        }
    }

    @DisplayName("score 계산")
    @Nested
    class ScoreCalculation {

        @Test
        @DisplayName("ORDER weight(0.7)가 VIEW weight(0.1)보다 크므로, 매출이 있는 상품이 조회만 있는 상품보다 높은 score를 가진다")
        void orderRevenueProductScoresHigher_thanViewOnlyProduct() {
            // arrange — PRODUCT_A: 조회 100회(score ≈ 0.46), PRODUCT_B: 매출 1000원(score ≈ 4.83)
            for (int i = 0; i < 100; i++) {
                rankingMetricsService.incrementViewCount(PRODUCT_A, today, TEST_HOUR);
            }
            rankingMetricsService.addOrderRevenue(PRODUCT_B, today, TEST_HOUR, new BigDecimal("1000"));

            // act
            rankingSyncScheduler.sync();

            // assert
            Double scoreA = masterRedisTemplate.opsForZSet().score(dailyKey, String.valueOf(PRODUCT_A));
            Double scoreB = masterRedisTemplate.opsForZSet().score(dailyKey, String.valueOf(PRODUCT_B));
            assertThat(scoreB).isGreaterThan(scoreA);
        }

        @Test
        @DisplayName("주문 1건(revenue=1)의 score가 좋아요 3건보다 높다 — ORDER(0.7) > LIKE(0.2)")
        void oneOrderScoresHigher_thanThreeLikes() {
            // arrange
            // PRODUCT_A: 좋아요 3건 → score = log(1+3) × 0.2 = ln(4) × 0.2 ≈ 0.277
            for (int i = 0; i < 3; i++) {
                rankingMetricsService.incrementLikeCount(PRODUCT_A, today, TEST_HOUR);
            }
            // PRODUCT_B: 주문 1건(revenue=1) → score = log(1+1) × 0.7 = ln(2) × 0.7 ≈ 0.485
            rankingMetricsService.addOrderRevenue(PRODUCT_B, today, TEST_HOUR, BigDecimal.ONE);

            // act
            rankingSyncScheduler.sync();

            // assert
            Double scoreA = masterRedisTemplate.opsForZSet().score(dailyKey, String.valueOf(PRODUCT_A));
            Double scoreB = masterRedisTemplate.opsForZSet().score(dailyKey, String.valueOf(PRODUCT_B));
            assertThat(scoreB).isGreaterThan(scoreA);
        }
    }

    @DisplayName("TTL 설정")
    @Nested
    class TtlSetting {

        @Test
        @DisplayName("스케줄러 실행 후 일간 랭킹 ZSET의 TTL은 오늘+2일 자정까지의 시간이다")
        void dailyZSetHasTtl_untilMidnightOfDayPlusTwo() {
            // arrange
            rankingMetricsService.incrementViewCount(PRODUCT_A, today, TEST_HOUR);
            long expectedMaxTtlSeconds = RankingKeys.dailyTtl(today).toSeconds();

            // act
            rankingSyncScheduler.sync();

            // assert — TTL > 0이고, dailyTtl() 계산값 이하(오차 1초 허용)
            long ttlSeconds = masterRedisTemplate.getExpire(dailyKey, TimeUnit.SECONDS);
            assertThat(ttlSeconds)
                    .isGreaterThan(0)
                    .isLessThanOrEqualTo(expectedMaxTtlSeconds + 1);
        }

        @Test
        @DisplayName("스케줄러 실행 후 시간별 랭킹 ZSET의 TTL은 오늘+2일 자정까지의 시간이다")
        void hourlyZSetHasTtl_untilMidnightOfDayPlusTwo() {
            // arrange
            rankingMetricsService.incrementViewCount(PRODUCT_A, today, TEST_HOUR);
            long expectedMaxTtlSeconds = RankingKeys.dailyTtl(today).toSeconds();

            // act
            rankingSyncScheduler.sync();

            // assert
            String hourlyKey = RankingKeys.hourlyKey(today, TEST_HOUR);
            long ttlSeconds = masterRedisTemplate.getExpire(hourlyKey, TimeUnit.SECONDS);
            assertThat(ttlSeconds)
                    .isGreaterThan(0)
                    .isLessThanOrEqualTo(expectedMaxTtlSeconds + 1);
        }
    }

    @DisplayName("멱등성")
    @Nested
    class Idempotency {

        @Test
        @DisplayName("스케줄러를 연속 두 번 실행해도 새 이벤트가 없으면 score가 변하지 않는다")
        void scoreUnchanged_whenSchedulerRunsTwiceWithNoNewEvents() {
            // arrange
            rankingMetricsService.incrementViewCount(PRODUCT_A, today, TEST_HOUR);

            // act
            rankingSyncScheduler.sync();
            Double scoreAfterFirst = masterRedisTemplate.opsForZSet().score(dailyKey, String.valueOf(PRODUCT_A));

            rankingSyncScheduler.sync();  // dirty=false라 재처리 없음
            Double scoreAfterSecond = masterRedisTemplate.opsForZSet().score(dailyKey, String.valueOf(PRODUCT_A));

            // assert
            assertThat(scoreAfterSecond).isEqualTo(scoreAfterFirst);
        }

        @Test
        @DisplayName("새 이벤트가 추가되면 스케줄러 재실행 시 score가 증가한다")
        void scoreIncreases_whenNewEventAdded() {
            // arrange
            rankingMetricsService.incrementViewCount(PRODUCT_A, today, TEST_HOUR);
            rankingSyncScheduler.sync();
            Double scoreAfterFirst = masterRedisTemplate.opsForZSet().score(dailyKey, String.valueOf(PRODUCT_A));

            // 새 이벤트 추가 → dirty=true 재설정
            rankingMetricsService.incrementLikeCount(PRODUCT_A, today, TEST_HOUR);

            // act
            rankingSyncScheduler.sync();
            Double scoreAfterSecond = masterRedisTemplate.opsForZSet().score(dailyKey, String.valueOf(PRODUCT_A));

            // assert
            assertThat(scoreAfterSecond).isGreaterThan(scoreAfterFirst);
        }
    }
}
