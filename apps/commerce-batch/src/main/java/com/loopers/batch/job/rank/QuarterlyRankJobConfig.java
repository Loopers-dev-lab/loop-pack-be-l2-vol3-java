package com.loopers.batch.job.rank;

import com.loopers.batch.job.rank.step.RankJobFactory;
import com.loopers.domain.rank.RankPeriodType;
import com.loopers.ranking.RankingKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = QuarterlyRankJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class QuarterlyRankJobConfig {

    public static final String JOB_NAME = "quarterlyRankJob";
    private static final String VALIDATION_STEP = "validateQuarterlyScoreCompletenessStep";
    private static final String BUILD_STEP = "buildQuarterlyRankStep";
    private static final String HEALTH_CHECK_STEP = "healthCheckQuarterlyRankStep";

    private final RankJobFactory rankJobFactory;
    private final JobRepository jobRepository;

    @Bean(JOB_NAME)
    public Job quarterlyRankJob(Step validateQuarterlyScoreCompletenessStep,
                                 Step buildQuarterlyRankStep,
                                 Step healthCheckQuarterlyRankStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(validateQuarterlyScoreCompletenessStep)
                .next(buildQuarterlyRankStep)
                .next(healthCheckQuarterlyRankStep)
                .build();
    }

    @Bean(VALIDATION_STEP)
    @JobScope
    public Step validateQuarterlyScoreCompletenessStep(@Value("#{jobParameters['date']}") String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, RankJobFactory.DATE_FMT);
        return rankJobFactory.buildValidationStep(
                VALIDATION_STEP,
                RankingKeyGenerator.quarterlyStart(date),
                RankingKeyGenerator.quarterlyEnd(date)
        );
    }

    @Bean(BUILD_STEP)
    @JobScope
    public Step buildQuarterlyRankStep(@Value("#{jobParameters['date']}") String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, RankJobFactory.DATE_FMT);
        return rankJobFactory.buildStep(
                BUILD_STEP,
                RankPeriodType.QUARTERLY,
                RankingKeyGenerator.quarterlyPeriodKey(date),
                RankingKeyGenerator.quarterlyStart(date),
                RankingKeyGenerator.quarterlyEnd(date)
        );
    }

    @Bean(HEALTH_CHECK_STEP)
    @JobScope
    public Step healthCheckQuarterlyRankStep(@Value("#{jobParameters['date']}") String dateStr,
                                              @Value("#{jobParameters['mode']}") String mode) {
        LocalDate date = LocalDate.parse(dateStr, RankJobFactory.DATE_FMT);
        boolean backfillMode = "backfill".equalsIgnoreCase(mode);
        return rankJobFactory.buildHealthCheckStep(
                HEALTH_CHECK_STEP,
                RankPeriodType.QUARTERLY,
                RankingKeyGenerator.quarterlyPeriodKey(date),
                RankingKeyGenerator.previousQuarterlyPeriodKey(date),
                backfillMode
        );
    }
}
