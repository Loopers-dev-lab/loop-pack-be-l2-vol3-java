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

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MonthlyRankJobConfig {

    public static final String JOB_NAME = "monthlyRankJob";
    private static final String VALIDATION_STEP = "validateMonthlyScoreCompletenessStep";
    private static final String BUILD_STEP = "buildMonthlyRankStep";
    private static final String HEALTH_CHECK_STEP = "healthCheckMonthlyRankStep";

    private final RankJobFactory rankJobFactory;
    private final JobRepository jobRepository;

    @Bean(JOB_NAME)
    public Job monthlyRankJob(Step validateMonthlyScoreCompletenessStep,
                               Step buildMonthlyRankStep,
                               Step healthCheckMonthlyRankStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(validateMonthlyScoreCompletenessStep)
                .next(buildMonthlyRankStep)
                .next(healthCheckMonthlyRankStep)
                .build();
    }

    @Bean(VALIDATION_STEP)
    @JobScope
    public Step validateMonthlyScoreCompletenessStep(@Value("#{jobParameters['date']}") String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, RankJobFactory.DATE_FMT);
        return rankJobFactory.buildValidationStep(
                VALIDATION_STEP,
                RankingKeyGenerator.monthStart(date),
                RankingKeyGenerator.monthEnd(date)
        );
    }

    @Bean(BUILD_STEP)
    @JobScope
    public Step buildMonthlyRankStep(@Value("#{jobParameters['date']}") String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, RankJobFactory.DATE_FMT);
        return rankJobFactory.buildStep(
                BUILD_STEP,
                RankPeriodType.MONTHLY,
                RankingKeyGenerator.monthlyPeriodKey(date),
                RankingKeyGenerator.monthStart(date),
                RankingKeyGenerator.monthEnd(date)
        );
    }

    @Bean(HEALTH_CHECK_STEP)
    @JobScope
    public Step healthCheckMonthlyRankStep(@Value("#{jobParameters['date']}") String dateStr,
                                            @Value("#{jobParameters['mode']}") String mode) {
        LocalDate date = LocalDate.parse(dateStr, RankJobFactory.DATE_FMT);
        boolean backfillMode = "backfill".equalsIgnoreCase(mode);
        return rankJobFactory.buildHealthCheckStep(
                HEALTH_CHECK_STEP,
                RankPeriodType.MONTHLY,
                RankingKeyGenerator.monthlyPeriodKey(date),
                RankingKeyGenerator.previousMonthlyPeriodKey(date),
                backfillMode
        );
    }
}
