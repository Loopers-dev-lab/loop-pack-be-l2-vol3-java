package com.loopers.batch.ranking.job;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.batch.RankingBatchJobParameters;
import com.loopers.domain.ranking.batch.RankingMvScoreCalculator;
import com.loopers.domain.ranking.batch.RankingScoreCandidate;
import com.loopers.domain.ranking.batch.RankingStagingRepository;
import com.loopers.domain.ranking.batch.RankingTop100Accumulator;
import com.loopers.infrastructure.ranking.batch.ProductMetricsEntity;
import com.loopers.infrastructure.ranking.batch.RedisRankingBatchLock;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.UnexpectedJobExecutionException;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Round 10 — 랭킹 MV 배치 Job(3단계): 파라미터 검증 → period 락 → staging 정리(자리) → 집계(자리) → publish(자리).
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingBatchJobParameters.JOB_NAME)
@Configuration
public class RankingBatchJobConfig {

    public static final String JOB_NAME = RankingBatchJobParameters.JOB_NAME;

    private static final String STEP_PERIOD_LOCK = "rankingPeriodLock";
    private static final String STEP_STAGING_CLEANUP = "rankingStagingCleanup";
    private static final String STEP_AGGREGATE = "rankingAggregate";
    private static final String STEP_PUBLISH = "rankingPublish";

    private final ObjectProvider<JobRepository> jobRepositoryProvider;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final PlatformTransactionManager transactionManager;

    public RankingBatchJobConfig(
            ObjectProvider<JobRepository> jobRepositoryProvider,
            JobListener jobListener,
            StepMonitorListener stepMonitorListener,
            @Lazy PlatformTransactionManager transactionManager
    ) {
        this.jobRepositoryProvider = jobRepositoryProvider;
        this.jobListener = jobListener;
        this.stepMonitorListener = stepMonitorListener;
        this.transactionManager = transactionManager;
    }

    private JobRepository jobRepository() {
        return jobRepositoryProvider.getObject();
    }

    /**
     * 리소스 없는 트랜잭션 매니저를 생성한다.
     *
     * @return ResourcelessTransactionManager
     */
    @Bean
    public ResourcelessTransactionManager rankingBatchResourcelessTransactionManager() {
        return new ResourcelessTransactionManager();
    }

    /**
     * Redis 랭킹 배치 락을 생성한다.
     *
     * @param redisTemplate RedisTemplate
     * @return RedisRankingBatchLock
     */
    @Bean
    public RedisRankingBatchLock rankingBatchLock(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate
    ) {
        return new RedisRankingBatchLock(redisTemplate);
    }

    /**
     * 랭킹 배치 파라미터 검증을 생성한다.
     *
     * @return JobParametersValidator
     */
    @Bean
    public JobParametersValidator rankingJobParametersValidator() {
        return new JobParametersValidator() {
            @Override
            public void validate(JobParameters parameters) throws JobParametersInvalidException {
                String period = parameters.getString(RankingBatchJobParameters.JOB_PARAM_PERIOD);
                String periodKey = parameters.getString(RankingBatchJobParameters.JOB_PARAM_PERIOD_KEY);
                if (period == null || period.isBlank()) {
                    throw new JobParametersInvalidException(
                            "Job 파라미터 period는 필수입니다. (WEEKLY 또는 MONTHLY)"
                    );
                }
                if (periodKey == null || periodKey.isBlank()) {
                    throw new JobParametersInvalidException("Job 파라미터 periodKey는 필수입니다.");
                }
                try {
                    RankingBatchJobParameters.validate(period, periodKey);
                } catch (IllegalArgumentException e) {
                    throw new JobParametersInvalidException(e.getMessage());
                }
            }
        };
    }

    /**
     * 랭킹 배치 락 해제를 생성한다.
     *
     * @param lock RedisRankingBatchLock
     * @return JobExecutionListener
     */
    @Bean
    public JobExecutionListener rankingBatchLockReleaseListener(RedisRankingBatchLock lock) {
        return new JobExecutionListener() {
            @Override
            public void beforeJob(JobExecution jobExecution) {
            }

            @Override
            public void afterJob(JobExecution jobExecution) {
                var ctx = jobExecution.getExecutionContext();
                Object lockHeld = ctx.get(RankingBatchJobParameters.CTX_LOCK_HELD);
                if (!"true".equals(lockHeld instanceof String ? (String) lockHeld : null)) {
                    return;
                }
                String period = jobExecution.getJobParameters().getString(RankingBatchJobParameters.JOB_PARAM_PERIOD);
                String periodKey = jobExecution.getJobParameters()
                        .getString(RankingBatchJobParameters.JOB_PARAM_PERIOD_KEY);
                Object ownerObj = ctx.get(RankingBatchJobParameters.CTX_LOCK_OWNER);
                String owner = ownerObj instanceof String ? (String) ownerObj : null;
                if (period != null && periodKey != null && owner != null) {
                    lock.releaseIfHeld(period, periodKey, owner);
                }
                ctx.remove(RankingBatchJobParameters.CTX_LOCK_HELD);
                ctx.remove(RankingBatchJobParameters.CTX_LOCK_OWNER);
            }
        };
    }

    /**
     * 랭킹 배치 락 획득을 생성한다.
     *
     * @param rankingBatchLock RedisRankingBatchLock
     * @param period 기간
     * @param periodKey 기간 키
     * @return Tasklet
     */
    @Bean
    @StepScope
    public Tasklet rankingPeriodLockTasklet(
            RedisRankingBatchLock rankingBatchLock,
            @Value("#{jobParameters['period']}") String period,
            @Value("#{jobParameters['periodKey']}") String periodKey
    ) {
        return (contribution, chunkContext) -> {
            long jobExecutionId = contribution.getStepExecution().getJobExecution().getId();
            String ownerToken = String.valueOf(jobExecutionId);
            if (!rankingBatchLock.tryAcquire(period, periodKey, ownerToken)) {
                throw new UnexpectedJobExecutionException(
                        "period 락을 획득하지 못했습니다. 다른 실행이 동일 period를 처리 중일 수 있습니다: "
                                + period + ":" + periodKey
                );
            }
            var jobCtx = contribution.getStepExecution().getJobExecution().getExecutionContext();
            jobCtx.putString(RankingBatchJobParameters.CTX_LOCK_HELD, "true");
            jobCtx.putString(RankingBatchJobParameters.CTX_LOCK_OWNER, ownerToken);
            return RepeatStatus.FINISHED;
        };
    }

    /**
     * 랭킹 배치 스테이징 정리를 생성한다.
     *
     * @param rankingStagingRepository RankingStagingRepository
     * @param period 기간
     * @param periodKey 기간 키
     * @return Tasklet
     */
    @Bean
    @StepScope
    public Tasklet rankingStagingCleanupTasklet(
            RankingStagingRepository rankingStagingRepository,
            @Value("#{jobParameters['period']}") String period,
            @Value("#{jobParameters['periodKey']}") String periodKey
    ) {
        return (contribution, chunkContext) -> {
            rankingStagingRepository.deleteByPeriodTypeAndPeriodKey(period, periodKey);
            return RepeatStatus.FINISHED;
        };
    }

    /**
     * 랭킹 배치 상품 메트릭스 리더를 생성한다.
     *
     * @param entityManagerFactory EntityManagerFactory
     * @return JpaPagingItemReader
     */
    @Bean
    @StepScope
    public JpaPagingItemReader<ProductMetricsEntity> rankingProductMetricsReader(
            EntityManagerFactory entityManagerFactory
    ) throws Exception {
        JpaPagingItemReader<ProductMetricsEntity> reader = new JpaPagingItemReaderBuilder<ProductMetricsEntity>()
                .name("rankingProductMetricsReader")
                .entityManagerFactory(entityManagerFactory)
                .pageSize(50)
                .queryString("select e from ProductMetricsEntity e order by e.productId asc")
                .build();
        reader.afterPropertiesSet();
        return reader;
    }

    /**
     * 랭킹 배치 상품 메트릭스 프로세서를 생성한다.
     *
     * @return ItemProcessor
     */
    @Bean
    @StepScope
    public ItemProcessor<ProductMetricsEntity, RankingScoreCandidate> rankingAggregateProcessor() {
        return entity -> new RankingScoreCandidate(
                entity.getProductId(),
                RankingMvScoreCalculator.score(
                        entity.getViewCount(),
                        entity.getLikeCount(),
                        entity.getSoldQuantity()
                )
        );
    }

    /**
     * 랭킹 배치 집계 누적기를 생성한다.
     *
     * @return RankingTop100Accumulator
     */
    @Bean
    @StepScope
    public RankingTop100Accumulator rankingTop100Accumulator() {
        return new RankingTop100Accumulator();
    }

    /** 
     * 랭킹 배치 집계 히프 라이터를 생성한다.
     *
     * @param rankingTop100Accumulator RankingTop100Accumulator
     * @return ItemWriter
     */
    @Bean
    @StepScope
    public ItemWriter<RankingScoreCandidate> rankingAggregateHeapWriter(
            RankingTop100Accumulator rankingTop100Accumulator
    ) {
        return chunk -> {
            for (RankingScoreCandidate candidate : chunk.getItems()) {
                rankingTop100Accumulator.accept(candidate);
            }
        };
    }
    
    /**
     * 랭킹 배치 집계 플러시 리스너를 생성한다.
     *
     * @param rankingTop100Accumulator RankingTop100Accumulator
     * @param rankingStagingRepository RankingStagingRepository
     * @return StepExecutionListener
     */
    @Bean
    @StepScope
    public StepExecutionListener rankingAggregateFlushListener(
            RankingTop100Accumulator rankingTop100Accumulator,
            RankingStagingRepository rankingStagingRepository
    ) {
        return new StepExecutionListener() {
            @Override
            public void beforeStep(StepExecution stepExecution) {
            }
    
            @Override
            public ExitStatus afterStep(StepExecution stepExecution) {
                if (!ExitStatus.COMPLETED.equals(stepExecution.getExitStatus())) {
                    return stepExecution.getExitStatus();
                }
                String periodType = stepExecution.getJobParameters()
                        .getString(RankingBatchJobParameters.JOB_PARAM_PERIOD);
                String periodKey = stepExecution.getJobParameters()
                        .getString(RankingBatchJobParameters.JOB_PARAM_PERIOD_KEY);
                if (periodType == null || periodKey == null) {
                    return ExitStatus.FAILED;
                }
                rankingStagingRepository.saveRankedRows(
                        periodType,
                        periodKey,
                        rankingTop100Accumulator.toSortedRankRows()
                );
                return ExitStatus.COMPLETED;
            }
        };
    }
    
    /**
     * 랭킹 배치 Job을 생성한다.
     *
     * @param rankingBatchResourcelessTransactionManager ResourcelessTransactionManager
     * @param rankingJobParametersValidator JobParametersValidator
     * @param rankingBatchLockReleaseListener JobExecutionListener
     * @param periodLockStep Step
     * @param stagingCleanupStep Step
     * @param aggregateStep Step
     * @param publishStep Step
     * @return Job
     */
    @Bean(JOB_NAME)
    public Job rankingProductMvJob(
            ResourcelessTransactionManager rankingBatchResourcelessTransactionManager,
            JobParametersValidator rankingJobParametersValidator,
            JobExecutionListener rankingBatchLockReleaseListener,
            @Qualifier(STEP_PERIOD_LOCK) Step periodLockStep,
            @Qualifier(STEP_STAGING_CLEANUP) Step stagingCleanupStep,
            @Qualifier(STEP_AGGREGATE) Step aggregateStep,
            @Qualifier(STEP_PUBLISH) Step publishStep
    ) {
        return new JobBuilder(JOB_NAME, jobRepository())
                .incrementer(new RunIdIncrementer())
                .validator(rankingJobParametersValidator)
                .listener(rankingBatchLockReleaseListener)
                .listener(jobListener)
                .start(periodLockStep)
                .next(stagingCleanupStep)
                .next(aggregateStep)
                .next(publishStep)
                .build();
    }

    /**
     * 랭킹 배치 락 획득을 생성한다.
     *
     * @param rankingBatchResourcelessTransactionManager ResourcelessTransactionManager
     * @param rankingPeriodLockTasklet Tasklet
     * @return Step
     */
    @Bean(STEP_PERIOD_LOCK)
    public Step periodLockStep(
            ResourcelessTransactionManager rankingBatchResourcelessTransactionManager,
            Tasklet rankingPeriodLockTasklet
    ) {
        return new StepBuilder(STEP_PERIOD_LOCK, jobRepository())
                .tasklet(rankingPeriodLockTasklet, rankingBatchResourcelessTransactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    /**
     * 랭킹 배치 스테이징 정리를 생성한다.
     *
     * @param rankingBatchResourcelessTransactionManager ResourcelessTransactionManager
     * @return Step
     */
    @Bean(STEP_STAGING_CLEANUP)
    public Step stagingCleanupStep(
            PlatformTransactionManager transactionManager,
            Tasklet rankingStagingCleanupTasklet
    ) {
        return new StepBuilder(STEP_STAGING_CLEANUP, jobRepository())
                .tasklet(rankingStagingCleanupTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    /**
     * 랭킹 배치 집계를 생성한다.
     *
     * @param rankingProductMetricsReader JpaPagingItemReader
     * @param rankingAggregateProcessor ItemProcessor
     * @param rankingAggregateHeapWriter ItemWriter
     * @param rankingAggregateFlushListener StepExecutionListener
     * @return Step
     */
    @Bean(STEP_AGGREGATE)
    public Step aggregateStep(
            JpaPagingItemReader<ProductMetricsEntity> rankingProductMetricsReader,
            ItemProcessor<ProductMetricsEntity, RankingScoreCandidate> rankingAggregateProcessor,
            ItemWriter<RankingScoreCandidate> rankingAggregateHeapWriter,
            StepExecutionListener rankingAggregateFlushListener
    ) {
        return new StepBuilder(STEP_AGGREGATE, jobRepository())
                .<ProductMetricsEntity, RankingScoreCandidate>chunk(50, transactionManager)
                .reader(rankingProductMetricsReader)
                .processor(rankingAggregateProcessor)
                .writer(rankingAggregateHeapWriter)
                .listener(rankingAggregateFlushListener)
                .listener(stepMonitorListener)
                .build();
    }

    /**
     * 랭킹 배치 발행을 생성한다.
     *
     * @param rankingBatchResourcelessTransactionManager ResourcelessTransactionManager
     * @return Step
     */
    @Bean(STEP_PUBLISH)
    public Step publishStep(ResourcelessTransactionManager rankingBatchResourcelessTransactionManager) {
        Tasklet noop = (contribution, chunkContext) -> RepeatStatus.FINISHED;
        return new StepBuilder(STEP_PUBLISH, jobRepository())
                .tasklet(noop, rankingBatchResourcelessTransactionManager)
                .listener(stepMonitorListener)
                .build();
    }
}
