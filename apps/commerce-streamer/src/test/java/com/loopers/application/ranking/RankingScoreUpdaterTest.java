package com.loopers.application.ranking;

import com.loopers.domain.ranking.ScoreFormula;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RankingScoreUpdaterTest {

    @Mock
    private RedisTemplate<String, String> writeTemplate;

    private RankingScoreUpdater updater;

    private static final long LAST_EVENT_AT = 1_712_700_000L;
    private static final ScoreFormula.Weights WEIGHTS = new ScoreFormula.Weights(0.1, 0.2, 0.7);

    @BeforeEach
    void setUp() {
        RankingProperties properties = new RankingProperties(
            WEIGHTS, 0.1, 0.97, 0,
            Map.of(), 0, null
        );
        updater = new RankingScoreUpdater(writeTemplate, properties);
    }

    @Nested
    @DisplayName("ScoreFormula 위임 검증")
    class ScoreFormulaDelegation {

        @Test
        @DisplayName("calculateScore()가 ScoreFormula.calculate()와 동일한 결과를 반환")
        void delegatesToScoreFormula() {
            double updaterScore = updater.calculateScore(100, 50, 80000, LAST_EVENT_AT, 0);
            double formulaScore = ScoreFormula.calculate(100, 50, 80000, 0, LAST_EVENT_AT, WEIGHTS);

            assertThat(updaterScore).isEqualTo(formulaScore);
        }

        @Test
        @DisplayName("categoryPriority 파라미터가 ScoreFormula에 올바르게 전달됨")
        void categoryPriorityPassedCorrectly() {
            double updaterScore = updater.calculateScore(0, 0, 0, LAST_EVENT_AT, 3);
            double formulaScore = ScoreFormula.calculate(0, 0, 0, 3, LAST_EVENT_AT, WEIGHTS);

            assertThat(updaterScore).isEqualTo(formulaScore);
        }

        @Test
        @DisplayName("음수 메트릭도 ScoreFormula와 동일하게 처리")
        void negativeMetrics_matchesFormula() {
            double updaterScore = updater.calculateScore(-5, -10, -50000, LAST_EVENT_AT, 0);
            double formulaScore = ScoreFormula.calculate(-5, -10, -50000, 0, LAST_EVENT_AT, WEIGHTS);

            assertThat(updaterScore).isEqualTo(formulaScore);
        }

        @Test
        @DisplayName("커스텀 가중치 — 가중치가 결과에 영향을 미침")
        void customWeights_affectScore() {
            ScoreFormula.Weights viewFirst = new ScoreFormula.Weights(0.7, 0.2, 0.1);
            RankingProperties viewFirstProps = new RankingProperties(
                viewFirst, 0.1, 0.97, 0, Map.of(), 0, null);
            RankingScoreUpdater viewUpdater = new RankingScoreUpdater(writeTemplate, viewFirstProps);

            double scoreView = viewUpdater.calculateScore(100, 0, 0, LAST_EVENT_AT, 0);
            double scoreOrder = viewUpdater.calculateScore(0, 0, 100, LAST_EVENT_AT, 0);

            double tiebreaker = LAST_EVENT_AT * ScoreFormula.TIEBREAKER_SCALE;
            assertThat(scoreView - tiebreaker).isGreaterThan(scoreOrder - tiebreaker);
        }
    }

    @Nested
    @DisplayName("키 생성")
    class KeyGeneration {

        @Test
        @DisplayName("ZSET 키: ranking:all:{yyyyMMdd} 형식")
        void zsetKey_format() {
            LocalDate date = LocalDate.of(2026, 4, 10);

            String key = RankingScoreUpdater.zsetKey(date);

            assertThat(key).isEqualTo("ranking:all:20260410");
        }

        @Test
        @DisplayName("ZSET 키: 커스텀 prefix 지원")
        void zsetKey_customPrefix() {
            LocalDate date = LocalDate.of(2026, 4, 10);

            String key = RankingScoreUpdater.zsetKey("ranking:exp:A:", date);

            assertThat(key).isEqualTo("ranking:exp:A:20260410");
        }

        @Test
        @DisplayName("주간 키: ranking:weekly:{yyyyMMdd} 형식")
        void weeklyKey_format() {
            LocalDate date = LocalDate.of(2026, 4, 10);

            String key = RankingScoreUpdater.weeklyKey(date);

            assertThat(key).isEqualTo("ranking:weekly:20260410");
        }

        @Test
        @DisplayName("월간 키: ranking:monthly:{yyyyMMdd} 형식")
        void monthlyKey_format() {
            LocalDate date = LocalDate.of(2026, 4, 10);

            String key = RankingScoreUpdater.monthlyKey(date);

            assertThat(key).isEqualTo("ranking:monthly:20260410");
        }

        @Test
        @DisplayName("Hash 키: ranking:metrics:{yyyyMMdd}:{productId} 형식")
        void hashKey_format() {
            LocalDate date = LocalDate.of(2026, 4, 10);

            String key = RankingScoreUpdater.hashKey(date, 101L);

            assertThat(key).isEqualTo("ranking:metrics:20260410:101");
        }

        @Test
        @DisplayName("날짜가 다르면 다른 키 생성")
        void differentDates_differentKeys() {
            LocalDate day1 = LocalDate.of(2026, 4, 10);
            LocalDate day2 = LocalDate.of(2026, 4, 11);

            assertThat(RankingScoreUpdater.zsetKey(day1))
                .isNotEqualTo(RankingScoreUpdater.zsetKey(day2));
            assertThat(RankingScoreUpdater.hashKey(day1, 101L))
                .isNotEqualTo(RankingScoreUpdater.hashKey(day2, 101L));
        }

        @Test
        @DisplayName("같은 날짜 + 다른 productId → 다른 Hash 키")
        void sameDate_differentProductId_differentHashKeys() {
            LocalDate date = LocalDate.of(2026, 4, 10);

            assertThat(RankingScoreUpdater.hashKey(date, 101L))
                .isNotEqualTo(RankingScoreUpdater.hashKey(date, 202L));
        }

        @Test
        @DisplayName("ZSET 키 prefix가 공개 상수와 일치")
        void zsetKey_usesPublicPrefix() {
            LocalDate date = LocalDate.of(2026, 4, 10);

            assertThat(RankingScoreUpdater.zsetKey(date))
                .startsWith(RankingScoreUpdater.RANKING_ZSET_PREFIX);
        }

        @Test
        @DisplayName("Hash 키 prefix가 공개 상수와 일치")
        void hashKey_usesPublicPrefix() {
            LocalDate date = LocalDate.of(2026, 4, 10);

            assertThat(RankingScoreUpdater.hashKey(date, 101L))
                .startsWith(RankingScoreUpdater.RANKING_METRICS_PREFIX);
        }

        @Test
        @DisplayName("ZSET TTL 상수가 8일(691200초)")
        void zsetTtlConstant_isEightDays() {
            assertThat(RankingScoreUpdater.RANKING_ZSET_TTL_SECONDS).isEqualTo(691_200L);
        }

        @Test
        @DisplayName("Hash TTL 상수가 2일(172800초)")
        void hashTtlConstant_isTwoDays() {
            assertThat(RankingScoreUpdater.RANKING_HASH_TTL_SECONDS).isEqualTo(172_800L);
        }

        @Test
        @DisplayName("집계 TTL 상수가 2일(172800초)")
        void aggregatedTtlConstant_isTwoDays() {
            assertThat(RankingScoreUpdater.RANKING_AGGREGATED_TTL_SECONDS).isEqualTo(172_800L);
        }
    }
}
