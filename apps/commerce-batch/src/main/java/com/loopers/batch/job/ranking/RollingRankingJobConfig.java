package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.batch.job.ranking.step.audit.AuditStepConfig;
import com.loopers.batch.job.ranking.step.promote.PromoteTopToMvStepConfig;
import com.loopers.batch.job.ranking.step.purge.PurgeMvStepConfig;
import com.loopers.batch.job.ranking.step.redis.RedisRefreshStepConfig;
import com.loopers.batch.job.ranking.step.score.ScoreAggregationStepConfig;
import com.loopers.batch.job.ranking.step.stage.StageLikeMetricsStepConfig;
import com.loopers.batch.job.ranking.step.stage.StageOrderMetricsStepConfig;
import com.loopers.batch.job.ranking.step.stage.StageViewMetricsStepConfig;
import com.loopers.batch.job.ranking.step.truncate.TruncateStagingTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 롤링 7일 / 30일 랭킹 배치 Job 구성.
 * 현재 Step 0 (스테이징 초기화) + Step 1 (View 적재) 가 연결되어 있으며,
 * 이후 커밋에서 Step 2~7 가 순차 추가된다.
 */
@Configuration
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RollingRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
public class RollingRankingJobConfig {

    public static final String JOB_NAME = "rollingRankingJob";
    public static final String STEP_TRUNCATE_STAGING = "truncateStagingStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final RankingJobParametersListener rankingJobParametersListener;
    private final TruncateStagingTasklet truncateStagingTasklet;

    @Bean(JOB_NAME)
    public Job rollingRankingJob(
            @Qualifier(StageViewMetricsStepConfig.STEP_NAME) Step stageViewMetricsStep,
            @Qualifier(StageLikeMetricsStepConfig.STEP_NAME) Step stageLikeMetricsStep,
            @Qualifier(StageOrderMetricsStepConfig.STEP_NAME) Step stageOrderMetricsStep,
            @Qualifier(PurgeMvStepConfig.STEP_LAST_7D) Step purgeLast7dMvStep,
            @Qualifier(PurgeMvStepConfig.STEP_LAST_30D) Step purgeLast30dMvStep,
            @Qualifier(ScoreAggregationStepConfig.STEP_NAME) Step scoreAggregationStep,
            @Qualifier(PromoteTopToMvStepConfig.STEP_NAME) Step promoteTopToMvStep,
            @Qualifier(AuditStepConfig.STEP_NAME) Step auditStep,
            @Qualifier(RedisRefreshStepConfig.STEP_NAME) Step redisRefreshStep
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .listener(jobListener)
                .listener(rankingJobParametersListener)
                .start(truncateStagingStep())
                .next(stageViewMetricsStep)
                .next(stageLikeMetricsStep)
                .next(stageOrderMetricsStep)
                .next(purgeLast7dMvStep)
                .next(purgeLast30dMvStep)
                .next(scoreAggregationStep)
                .next(promoteTopToMvStep)
                .next(auditStep)
                .next(redisRefreshStep)
                .build();
    }

    @Bean(STEP_TRUNCATE_STAGING)
    public Step truncateStagingStep() {
        return new StepBuilder(STEP_TRUNCATE_STAGING, jobRepository)
                .tasklet(truncateStagingTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }
}
