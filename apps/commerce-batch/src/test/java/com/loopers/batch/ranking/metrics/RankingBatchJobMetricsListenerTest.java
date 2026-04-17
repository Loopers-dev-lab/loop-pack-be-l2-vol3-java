package com.loopers.batch.ranking.metrics;

import com.loopers.domain.ranking.batch.RankingBatchJobParameters;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingBatchJobMetricsListenerTest {

    @Mock
    private RankingBatchJobMetrics rankingBatchJobMetrics;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private RankingBatchJobMetricsListener listener;

    @BeforeEach
    void setUp() {
        listener = new RankingBatchJobMetricsListener(meterRegistry, rankingBatchJobMetrics);
    }

    @Test
    @DisplayName("COMPLETED이면 recordSuccess 호출")
    void afterJob_whenCompleted_shouldRecordSuccess() {
        JobExecution execution = mockJobExecution(RankingBatchJobParameters.JOB_NAME, BatchStatus.COMPLETED);

        listener.afterJob(execution);

        verify(rankingBatchJobMetrics).recordSuccess("WEEKLY", "2026W15");
        assertThat(meterRegistry.find("batch.rank.job.failure.count").meters()).isEmpty();
    }

    @Test
    @DisplayName("FAILED이면 failure 카운터 증가")
    void afterJob_whenFailed_shouldIncrementFailureCounter() {
        JobExecution execution = mockJobExecution(RankingBatchJobParameters.JOB_NAME, BatchStatus.FAILED);

        listener.afterJob(execution);

        verify(rankingBatchJobMetrics, never()).recordSuccess(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        assertThat(
                meterRegistry.counter(
                        "batch.rank.job.failure.count",
                        "period", "WEEKLY",
                        "period_key", "2026W15",
                        "batch_status", "FAILED"
                ).count()
        ).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("다른 Job이면 메트릭 없음")
    void afterJob_whenOtherJob_shouldNoop() {
        JobInstance inst = mock(JobInstance.class);
        when(inst.getJobName()).thenReturn("otherJob");
        JobExecution execution = mock(JobExecution.class);
        when(execution.getJobInstance()).thenReturn(inst);

        listener.afterJob(execution);

        verify(rankingBatchJobMetrics, never()).recordSuccess(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        assertThat(meterRegistry.find("batch.rank.job.failure.count").meters()).isEmpty();
    }

    private static JobExecution mockJobExecution(String jobName, BatchStatus status) {
        JobInstance inst = mock(JobInstance.class);
        when(inst.getJobName()).thenReturn(jobName);
        JobParameters params = new JobParametersBuilder()
                .addString(RankingBatchJobParameters.JOB_PARAM_PERIOD, "WEEKLY")
                .addString(RankingBatchJobParameters.JOB_PARAM_PERIOD_KEY, "2026W15")
                .toJobParameters();
        JobExecution execution = mock(JobExecution.class);
        when(execution.getJobInstance()).thenReturn(inst);
        when(execution.getJobParameters()).thenReturn(params);
        when(execution.getStatus()).thenReturn(status);
        return execution;
    }
}
