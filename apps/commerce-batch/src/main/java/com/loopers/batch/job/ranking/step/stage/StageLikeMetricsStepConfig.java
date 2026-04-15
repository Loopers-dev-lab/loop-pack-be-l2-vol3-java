package com.loopers.batch.job.ranking.step.stage;

import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.staging.StagingRankingAggregation;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class StageLikeMetricsStepConfig {

    public static final String STEP_NAME = "stageLikeMetricsStep";
    private static final int CHUNK_SIZE = 500;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final StepMonitorListener stepMonitorListener;
    private final LikeMetricStreamingReader reader;
    private final StagingLikeAggregationProcessor processor;
    private final StagingLikeMetricsWriter writer;

    @Bean(STEP_NAME)
    public Step stageLikeMetricsStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<AggregatedMetric, List<StagingRankingAggregation>>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener(stepMonitorListener)
                .build();
    }
}
