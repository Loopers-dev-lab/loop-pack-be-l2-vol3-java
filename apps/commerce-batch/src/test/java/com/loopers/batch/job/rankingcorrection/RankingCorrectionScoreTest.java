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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * RankingCorrectionJobConfig의 score 계산 검증.
 * RankingScoreUpdater(commerce-streamer)와 동일한 수식이 적용되는지 확인.
 */
@ExtendWith(MockitoExtension.class)
class RankingCorrectionScoreTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobListener jobListener;
    @Mock private StepMonitorListener stepMonitorListener;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private DataSource dataSource;

    private RankingCorrectionJobConfig config;
    private static final double EPSILON = 1e-10;

    @BeforeEach
    void setUp() {
        RankingCorrectionProperties properties = new RankingCorrectionProperties(
            new RankingCorrectionProperties.Weights(0.1, 0.2, 0.7)
        );
        config = new RankingCorrectionJobConfig(
            jobRepository, jobListener, stepMonitorListener, transactionManager,
            dataSource, properties
        );
    }

    @Nested
    @DisplayName("Score 수식 일치 — RankingScoreUpdater와 동일")
    class ScoreFormula {

        @Test
        @DisplayName("모든 메트릭 0 → tiebreaker만 남음")
        void allZeros() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 0, 0, 0, 0);
            double score = config.calculateScore(row);
            assertThat(score).isCloseTo(1L * EPSILON, within(1e-15));
        }

        @Test
        @DisplayName("view=99, like=0, sales=0 → 0.1 × log₁₀(100) = 0.2")
        void viewOnly() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 99, 0, 0, 0);
            double score = config.calculateScore(row);
            assertThat(score).isCloseTo(0.2, within(1e-9));
        }

        @Test
        @DisplayName("view=0, like=99, sales=0 → 0.2 × log₁₀(100) = 0.4")
        void likeOnly() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 0, 99, 0, 0);
            double score = config.calculateScore(row);
            assertThat(score).isCloseTo(0.4, within(1e-9));
        }

        @Test
        @DisplayName("view=0, like=0, salesAmount=9999 → 0.7 × log₁₀(10000) = 2.8")
        void orderOnly() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 0, 0, 0, 9999);
            double score = config.calculateScore(row);
            assertThat(score).isCloseTo(2.8, within(1e-9));
        }

        @Test
        @DisplayName("복합 score: view=100 + like=10 + salesAmount=50000")
        void compositeScore() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 100, 10, 0, 50000);
            double score = config.calculateScore(row);

            double expected = 0.1 * Math.log10(101)
                + 0.2 * Math.log10(11)
                + 0.7 * Math.log10(50001)
                + 1L * EPSILON;
            assertThat(score).isCloseTo(expected, within(1e-15));
        }
    }

    @Nested
    @DisplayName("음수 메트릭 방어")
    class NegativeDefense {

        @Test
        @DisplayName("음수 netLike → 0으로 클램핑")
        void negativeLike() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 0, -10, 0, 0);
            double score = config.calculateScore(row);
            assertThat(score).isCloseTo(1L * EPSILON, within(1e-15));
        }

        @Test
        @DisplayName("음수 netSalesAmount → 0으로 클램핑")
        void negativeSalesAmount() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 0, 0, 0, -50000);
            double score = config.calculateScore(row);
            assertThat(score).isCloseTo(1L * EPSILON, within(1e-15));
        }
    }

    @Nested
    @DisplayName("타이브레이커")
    class Tiebreaker {

        @Test
        @DisplayName("동점 시 높은 productId가 상위")
        void higherProductId_higherScore() {
            ProductMetricsRow oldProduct = new ProductMetricsRow(101L, 50, 10, 5, 10000);
            ProductMetricsRow newProduct = new ProductMetricsRow(505L, 50, 10, 5, 10000);

            double scoreOld = config.calculateScore(oldProduct);
            double scoreNew = config.calculateScore(newProduct);
            assertThat(scoreNew).isGreaterThan(scoreOld);
        }
    }
}
