package com.loopers.application.ranking;

import com.loopers.domain.ranking.ProductDailySignalRepository;
import com.loopers.domain.ranking.RankingKeyGenerator;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({RedisTestContainersConfig.class, MySqlTestContainersConfig.class})
@DisplayName("재집계 통합 테스트 — DB 신호 → ScoreAggregator → Shadow ZSET → RENAME")
class RankingRecalculationIntegrationTest {

    @Autowired
    private RankingRecalculationApp recalculationApp;

    @Autowired
    private RankingApp rankingApp;

    @Autowired
    private ProductDailySignalRepository productDailySignalRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final LocalDate DATE = LocalDate.of(2026, 4, 8);

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("재집계 정합성")
    class RecalculationAccuracy {

        @Test
        @DisplayName("이벤트 적재 → 재집계 → ZSET 점수가 DB 원본 × 가중치와 일치한다")
        void recalculatedScoresMatchDbSignals() {
            rankingApp.applyViewScore(1L, DATE);
            rankingApp.applyViewScore(1L, DATE);
            rankingApp.applyViewScore(1L, DATE);
            rankingApp.applyLikeDelta(1L, 1, DATE);
            rankingApp.applyLikeDelta(1L, 1, DATE);
            rankingApp.applyOrderScore(1L, new BigDecimal("5000"), 2, DATE);

            long count = recalculationApp.recalculate(DATE);
            assertThat(count).isEqualTo(1L);

            String mainKey = RankingKeyGenerator.dailyKey(DATE);
            Double score = redisTemplate.opsForZSet().score(mainKey, "1");
            double expected = 0.1 * 3 + 0.2 * 2 + 0.7 * 10000.0;
            assertThat(score).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("여러 상품 재집계 → 모든 상품 점수가 DB 기반으로 갱신된다")
        void multipleProductsRecalculated() {
            rankingApp.applyViewScore(1L, DATE);
            rankingApp.applyOrderScore(1L, new BigDecimal("10000"), 1, DATE);

            rankingApp.applyViewScore(2L, DATE);
            rankingApp.applyViewScore(2L, DATE);
            rankingApp.applyLikeDelta(2L, 1, DATE);

            long count = recalculationApp.recalculate(DATE);
            assertThat(count).isEqualTo(2L);

            String mainKey = RankingKeyGenerator.dailyKey(DATE);
            Double score1 = redisTemplate.opsForZSet().score(mainKey, "1");
            Double score2 = redisTemplate.opsForZSet().score(mainKey, "2");

            assertThat(score1).isCloseTo(0.1 * 1 + 0.7 * 10000.0, org.assertj.core.data.Offset.offset(0.01));
            assertThat(score2).isCloseTo(0.1 * 2 + 0.2 * 1, org.assertj.core.data.Offset.offset(0.01));
        }

        @Test
        @DisplayName("재집계는 tie-break fraction을 제거하고 순수 가중치 점수로 교체한다")
        void recalculationRemovesTieBreakFraction() {
            rankingApp.applyViewScore(1L, DATE);

            String mainKey = RankingKeyGenerator.dailyKey(DATE);
            Double scoreBeforeRecalc = redisTemplate.opsForZSet().score(mainKey, "1");
            assertThat(scoreBeforeRecalc).isGreaterThan(0.1);

            recalculationApp.recalculate(DATE);

            Double scoreAfterRecalc = redisTemplate.opsForZSet().score(mainKey, "1");
            assertThat(scoreAfterRecalc).isCloseTo(0.1, org.assertj.core.data.Offset.offset(0.0001));
        }
    }

    @Nested
    @DisplayName("Shadow ZSET 격리")
    class ShadowIsolation {

        @Test
        @DisplayName("재집계 완료 후 shadow key는 존재하지 않는다")
        void shadowKeyRemovedAfterRename() {
            rankingApp.applyViewScore(1L, DATE);

            recalculationApp.recalculate(DATE);

            String shadowKey = RankingKeyGenerator.shadowKey(DATE);
            Boolean exists = redisTemplate.hasKey(shadowKey);
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("재집계 전 기존 ZSET의 순위와 재집계 후 순위가 올바르게 교체된다")
        void rankingOrderUpdatedAfterRecalculation() {
            rankingApp.applyOrderScore(1L, new BigDecimal("100"), 1, DATE);
            rankingApp.applyOrderScore(2L, new BigDecimal("10000"), 1, DATE);

            String mainKey = RankingKeyGenerator.dailyKey(DATE);
            Long rankBefore = redisTemplate.opsForZSet().reverseRank(mainKey, "2");
            assertThat(rankBefore).isEqualTo(0L);

            recalculationApp.recalculate(DATE);

            Set<ZSetOperations.TypedTuple<String>> results =
                    redisTemplate.opsForZSet().reverseRangeWithScores(mainKey, 0, -1);
            assertThat(results).isNotNull().hasSize(2);

            ZSetOperations.TypedTuple<String> first = results.iterator().next();
            assertThat(first.getValue()).isEqualTo("2");
            assertThat(first.getScore()).isCloseTo(0.7 * 10000.0, org.assertj.core.data.Offset.offset(0.01));
        }
    }

    @Nested
    @DisplayName("엣지 케이스")
    class EdgeCases {

        @Test
        @DisplayName("신호 데이터가 없으면 0을 반환하고 기존 ZSET을 변경하지 않는다")
        void noSignalsDoesNotAffectExistingZset() {
            LocalDate emptyDate = LocalDate.of(2026, 1, 1);

            long count = recalculationApp.recalculate(emptyDate);

            assertThat(count).isEqualTo(0L);
            Boolean exists = redisTemplate.hasKey(RankingKeyGenerator.dailyKey(emptyDate));
            assertThat(exists).isFalse();
        }
    }
}
