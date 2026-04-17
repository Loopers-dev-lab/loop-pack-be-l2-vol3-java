package com.loopers.batch.job.rankingcorrection;

import com.loopers.batch.job.rankingcorrection.RankingCorrectionJobConfig.ProductMetricsRow;
import com.loopers.domain.ranking.ScoreFormula;
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

@ExtendWith(MockitoExtension.class)
class RankingCorrectionScoreTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobListener jobListener;
    @Mock private StepMonitorListener stepMonitorListener;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private DataSource dataSource;

    private RankingCorrectionJobConfig config;
    private static final ScoreFormula.Weights WEIGHTS = new ScoreFormula.Weights(0.1, 0.2, 0.7);
    private static final long FIXED_EPOCH = 1_712_700_000L;

    @BeforeEach
    void setUp() {
        RankingCorrectionProperties properties = new RankingCorrectionProperties(
            WEIGHTS, Map.of(100L, 2), 0
        );
        config = new RankingCorrectionJobConfig(
            jobRepository, jobListener, stepMonitorListener, transactionManager,
            dataSource, properties
        );
    }

    @Nested
    @DisplayName("ScoreFormula 위임 검증")
    class ScoreFormulaDelegation {

        @Test
        @DisplayName("calculateScore()가 ScoreFormula.calculate()와 동일한 결과를 반환")
        void delegatesToScoreFormula() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 100, 50, 10, 80000, null);

            double configScore = config.calculateScore(row, 0, FIXED_EPOCH);
            double formulaScore = ScoreFormula.calculate(100, 50, 80000, 0, FIXED_EPOCH, WEIGHTS);

            assertThat(configScore).isEqualTo(formulaScore);
        }

        @Test
        @DisplayName("categoryPriority가 ScoreFormula에 올바르게 전달됨")
        void categoryPriorityPassedCorrectly() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 0, 0, 0, 0, 100L);

            double configScore = config.calculateScore(row, 2, FIXED_EPOCH);
            double formulaScore = ScoreFormula.calculate(0, 0, 0, 2, FIXED_EPOCH, WEIGHTS);

            assertThat(configScore).isEqualTo(formulaScore);
        }

        @Test
        @DisplayName("음수 메트릭도 ScoreFormula와 동일하게 처리")
        void negativeMetrics_matchesFormula() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 0, -10, 0, -50000, null);

            double configScore = config.calculateScore(row, 0, FIXED_EPOCH);
            double formulaScore = ScoreFormula.calculate(0, -10, -50000, 0, FIXED_EPOCH, WEIGHTS);

            assertThat(configScore).isEqualTo(formulaScore);
        }
    }

    @Nested
    @DisplayName("resolveCategoryPriority")
    class ResolveCategoryPriority {

        @Test
        @DisplayName("categoryPriority 매핑이 있으면 해당 값 사용")
        void withMapping_usesMappedValue() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 100, 50, 10, 80000, 100L);

            double score = config.calculateScore(row, 2, FIXED_EPOCH);
            double expected = ScoreFormula.calculate(100, 50, 80000, 2, FIXED_EPOCH, WEIGHTS);

            assertThat(score).isEqualTo(expected);
        }

        @Test
        @DisplayName("categoryPriority 매핑이 없으면 defaultCategoryPriority 사용")
        void withoutMapping_usesDefault() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 100, 50, 10, 80000, 999L);

            double score = config.calculateScore(row, 0, FIXED_EPOCH);
            double expected = ScoreFormula.calculate(100, 50, 80000, 0, FIXED_EPOCH, WEIGHTS);

            assertThat(score).isEqualTo(expected);
        }

        @Test
        @DisplayName("categoryId가 null이면 defaultCategoryPriority 사용")
        void nullCategoryId_usesDefault() {
            ProductMetricsRow row = new ProductMetricsRow(1L, 0, 0, 0, 0, null);

            double scoreNoPriority = config.calculateScore(row, 0, FIXED_EPOCH);
            double scoreWithPriority = config.calculateScore(row, 1, FIXED_EPOCH);

            assertThat(scoreWithPriority - scoreNoPriority).isCloseTo(1.0, within(1e-10));
        }
    }
}
