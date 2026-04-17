package com.loopers.batch.ranking.metrics;

import com.loopers.domain.ranking.batch.RankingBatchJobParameters;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * 랭킹 MV 배치 실패 카운트·성공 시각 메트릭. 로드맵 3.7.
 */
@Component("rankingBatchJobMetricsListener")
public class RankingBatchJobMetricsListener implements JobExecutionListener {

    private final MeterRegistry meterRegistry;
    private final RankingBatchJobMetrics rankingBatchJobMetrics;

    /**
     * 랭킹 배치 실패 카운트·성공 시각 메트릭 리스너를 생성한다.
     *
     * @param meterRegistry MeterRegistry
     * @param rankingBatchJobMetrics RankingBatchJobMetrics
     */
    public RankingBatchJobMetricsListener(
            MeterRegistry meterRegistry,
            RankingBatchJobMetrics rankingBatchJobMetrics) {
        this.meterRegistry = meterRegistry;
        this.rankingBatchJobMetrics = rankingBatchJobMetrics;
    }

    /**
     * 랭킹 배치 실행 전 작업을 수행한다.
     *
     * @param jobExecution JobExecution
     */
    @Override
    public void beforeJob(JobExecution jobExecution) {
    }

    /**
     * 랭킹 배치 실행 후 작업을 수행한다.
     *
     * @param jobExecution JobExecution
     */
    @Override
    public void afterJob(JobExecution jobExecution) {
        if (!RankingBatchJobParameters.JOB_NAME.equals(jobExecution.getJobInstance().getJobName())) {
            return;
        }
        String period = jobExecution.getJobParameters().getString(RankingBatchJobParameters.JOB_PARAM_PERIOD);
        String periodKey = jobExecution.getJobParameters().getString(RankingBatchJobParameters.JOB_PARAM_PERIOD_KEY);
        if (period == null || periodKey == null) {
            return;
        }
        BatchStatus status = jobExecution.getStatus();
        if (status == BatchStatus.COMPLETED) {
            rankingBatchJobMetrics.recordSuccess(period, periodKey);
            return;
        }
        if (status.isUnsuccessful()) {
            Counter.builder("batch.rank.job.failure.count")
                    .tags("period", period, "period_key", periodKey, "batch_status", status.name())
                    .register(meterRegistry)
                    .increment();
        }
    }
}
