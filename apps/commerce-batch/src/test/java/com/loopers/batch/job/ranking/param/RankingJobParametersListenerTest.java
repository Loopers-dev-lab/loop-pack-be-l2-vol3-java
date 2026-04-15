package com.loopers.batch.job.ranking.param;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.MetaDataInstanceFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class RankingJobParametersListenerTest {

    private final RankingJobParametersListener listener = new RankingJobParametersListener();

    @DisplayName("beforeJob 은 anchorDate 파라미터로부터 ExecutionContext 에 경계 값 5개를 주입한다.")
    @Test
    void populatesExecutionContextFromAnchorDate() {
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

    @DisplayName("anchorDate 파라미터가 없으면 예외를 던진다 (Bounded 위반 차단).")
    @Test
    void rejectsMissingAnchorDate() {
        JobExecution execution = MetaDataInstanceFactory.createJobExecution(
                "rollingRankingJob", 1L, 2L,
                new JobParametersBuilder().toJobParameters()
        );

        assertThatThrownBy(() -> listener.beforeJob(execution))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @DisplayName("재시작으로 ExecutionContext 에 이미 값이 있으면 덮어쓰지 않는다 (Bounded 유지).")
    @Test
    void doesNotOverwriteOnRestart() {
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
