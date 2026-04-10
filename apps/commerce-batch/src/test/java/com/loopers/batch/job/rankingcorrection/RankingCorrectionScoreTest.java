package com.loopers.batch.job.rankingcorrection;

import com.loopers.batch.job.rankingcorrection.RankingCorrectionJobConfig.ProductMetricsRow;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * RankingCorrectionJobConfig의 score 계산 검증.
 * RankingScoreUpdater(commerce-streamer)와 동일한 수식이 적용되는지 확인.
 *
 * <p>수식 (v2 — 0~1 정규화):
 * {@code categoryPriority + W(view)×log₁₀(viewCount+1)/MAX_LOG + W(like)×log₁₀(likeCount+1)/MAX_LOG
 *   + W(order)×log₁₀(salesAmount+1)/MAX_LOG + lastEventEpochSeconds × TIEBREAKER_SCALE}</p>
 */
@ExtendWith(MockitoExtension.class)
class RankingCorrectionScoreTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobListener jobListener;
    @Mock private StepMonitorListener stepMonitorListener;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private DataSource dataSource;

    private RankingCorrectionJobConfig config;
    private static final double MAX_LOG = 7.0;
    private static final double TIEBREAKER_SCALE = 1e-16;
    private static final long FIXED_EPOCH = 1_712_700_000L;

    @BeforeEach
    void setUp() {
        RankingCorrectionProperties properties = new RankingCorrectionProperties(
            new RankingCorrectionProperties.Weights(0.1, 0.2, 0.7),
            Map.of(), 0
        );
        config = new RankingCorrectionJobConfig(
            jobRepository, jobListener, stepMonitorListener, transactionManager,
            dataSource, properties
        );
    }

    @Nested
    @DisplayName("Score 수식 일치 — RankingScoreUpdater와 동일 (v2 정규화)")
    class ScoreFormula {

        @Test
        @DisplayName("모든 메트릭 0 → tiebreaker만 남음")
        void allZeros() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 0, 0, 0, 0, null);
            double score = config.calculateScore(row, 0, FIXED_EPOCH);
            assertThat(score).isCloseTo(FIXED_EPOCH * TIEBREAKER_SCALE, within(1e-20));
        }

        @Test
        @DisplayName("view=99 → 0.1 × log₁₀(100) / 7 ≈ 0.02857")
        void viewOnly() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 99, 0, 0, 0, null);
            double score = config.calculateScore(row, 0, FIXED_EPOCH);
            double expected = 0.1 * Math.log10(100) / MAX_LOG + FIXED_EPOCH * TIEBREAKER_SCALE;
            assertThat(score).isCloseTo(expected, within(1e-15));
        }

        @Test
        @DisplayName("like=99 → 0.2 × log₁₀(100) / 7 ≈ 0.05714")
        void likeOnly() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 0, 99, 0, 0, null);
            double score = config.calculateScore(row, 0, FIXED_EPOCH);
            double expected = 0.2 * Math.log10(100) / MAX_LOG + FIXED_EPOCH * TIEBREAKER_SCALE;
            assertThat(score).isCloseTo(expected, within(1e-15));
        }

        @Test
        @DisplayName("salesAmount=9999 → 0.7 × log₁₀(10000) / 7 = 0.4")
        void orderOnly() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 0, 0, 0, 9999, null);
            double score = config.calculateScore(row, 0, FIXED_EPOCH);
            double expected = 0.7 * Math.log10(10000) / MAX_LOG + FIXED_EPOCH * TIEBREAKER_SCALE;
            assertThat(score).isCloseTo(expected, within(1e-15));
        }

        @Test
        @DisplayName("복합 score: view=100 + like=10 + salesAmount=50000")
        void compositeScore() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 100, 10, 0, 50000, null);
            double score = config.calculateScore(row, 0, FIXED_EPOCH);

            double expected = 0.1 * Math.log10(101) / MAX_LOG
                + 0.2 * Math.log10(11) / MAX_LOG
                + 0.7 * Math.log10(50001) / MAX_LOG
                + FIXED_EPOCH * TIEBREAKER_SCALE;
            assertThat(score).isCloseTo(expected, within(1e-15));
        }
    }

    @Nested
    @DisplayName("음수 메트릭 방어")
    class NegativeDefense {

        @Test
        @DisplayName("음수 netLike → 0으로 클램핑")
        void negativeLike() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 0, -10, 0, 0, null);
            double score = config.calculateScore(row, 0, FIXED_EPOCH);
            assertThat(score).isCloseTo(FIXED_EPOCH * TIEBREAKER_SCALE, within(1e-20));
        }

        @Test
        @DisplayName("음수 netSalesAmount → 0으로 클램핑")
        void negativeSalesAmount() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 0, 0, 0, -50000, null);
            double score = config.calculateScore(row, 0, FIXED_EPOCH);
            assertThat(score).isCloseTo(FIXED_EPOCH * TIEBREAKER_SCALE, within(1e-20));
        }
    }

    @Nested
    @DisplayName("타이브레이커 — lastEventAt × TIEBREAKER_SCALE")
    class Tiebreaker {

        @Test
        @DisplayName("동점 시 최근 이벤트가 상위")
        void laterEvent_higherScore() {
            ProductMetricsRow row = new ProductMetricsRow(101L, 50, 10, 5, 10000, null);

            long earlier = 1_712_700_000L;
            long later = 1_712_700_100L;

            double scoreOld = config.calculateScore(row, 0, earlier);
            double scoreNew = config.calculateScore(row, 0, later);
            assertThat(scoreNew).isGreaterThan(scoreOld);
        }
    }

    @Nested
    @DisplayName("카테고리 우선순위")
    class CategoryPriority {

        @Test
        @DisplayName("categoryPriority가 정수부에 반영")
        void categoryPriority_addsToScore() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 0, 0, 0, 0, 100L);

            double scoreNoPriority = config.calculateScore(row, 0, FIXED_EPOCH);
            double scoreWithPriority = config.calculateScore(row, 1, FIXED_EPOCH);

            assertThat(scoreWithPriority - scoreNoPriority).isCloseTo(1.0, within(1e-10));
        }
    }
}
