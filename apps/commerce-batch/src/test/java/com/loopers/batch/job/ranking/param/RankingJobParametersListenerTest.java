package com.loopers.batch.job.ranking.param;

import com.loopers.domain.ranking.weight.WeightConfig;
import com.loopers.domain.ranking.weight.WeightConfigRepository;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.MetaDataInstanceFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RankingJobParametersListenerTest {

    private final WeightConfigRepository stubRepo = new WeightConfigRepository() {
        @Override public WeightConfig save(WeightConfig entity) { return entity; }
        @Override public List<WeightConfig> findAllByActiveTrue() {
            return List.of(new WeightConfig("control", 0.1, 0.2, 0.7, 100, true));
        }
    };

    private final RankingJobParametersListener listener = new RankingJobParametersListener(stubRepo);

    @Nested
    class ExecutionContext_주입 {

        @Test
        void anchorDate_파라미터로부터_경계_값_5개를_주입한다() {
            JobExecution execution = MetaDataInstanceFactory.createJobExecution(
                    "rollingRankingJob", 1L, 1L,
                    new JobParametersBuilder()
                            .addString(RankingJobParametersListener.PARAM_ANCHOR_DATE, "20260414")
                            .toJobParameters()
            );

            listener.beforeJob(execution);

            var ctx = execution.getExecutionContext();
            assertAll(
                    () -> assertThat(ctx.getString(RankingJobParametersListener.CTX_ANCHOR_DATE_KEY)).isEqualTo("20260414"),
                    () -> assertThat(ctx.getString(RankingJobParametersListener.CTX_LAST_7D_START)).isEqualTo("2026-04-08T00:00"),
                    () -> assertThat(ctx.getString(RankingJobParametersListener.CTX_LAST_7D_END)).isEqualTo("2026-04-15T00:00"),
                    () -> assertThat(ctx.getString(RankingJobParametersListener.CTX_LAST_30D_START)).isEqualTo("2026-03-16T00:00"),
                    () -> assertThat(ctx.getString(RankingJobParametersListener.CTX_LAST_30D_END)).isEqualTo("2026-04-15T00:00")
            );
        }

        @Test
        void anchorDate_파라미터가_없으면_예외를_던진다() {
            JobExecution execution = MetaDataInstanceFactory.createJobExecution(
                    "rollingRankingJob", 1L, 2L,
                    new JobParametersBuilder().toJobParameters()
            );

            assertThatThrownBy(() -> listener.beforeJob(execution))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class 재시작 {

        @Test
        void ExecutionContext_에_이미_값이_있으면_덮어쓰지_않는다() {
            JobExecution execution = MetaDataInstanceFactory.createJobExecution(
                    "rollingRankingJob", 1L, 3L,
                    new JobParametersBuilder()
                            .addString(RankingJobParametersListener.PARAM_ANCHOR_DATE, "20260414")
                            .toJobParameters()
            );
            // 첫 실행이 남긴 값을 모방
            execution.getExecutionContext().putString(
                    RankingJobParametersListener.CTX_ANCHOR_DATE_KEY, "20260101");
            execution.getExecutionContext().putString(
                    RankingJobParametersListener.CTX_LAST_7D_START, "2025-12-26T00:00");

            listener.beforeJob(execution);

            var ctx = execution.getExecutionContext();
            assertAll(
                    () -> assertThat(ctx.getString(RankingJobParametersListener.CTX_ANCHOR_DATE_KEY)).isEqualTo("20260101"),
                    () -> assertThat(ctx.getString(RankingJobParametersListener.CTX_LAST_7D_START)).isEqualTo("2025-12-26T00:00")
            );
        }
    }
}
