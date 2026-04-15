package com.loopers.batch.job.rank;

import com.loopers.batch.job.rank.step.RankJobFactory;
import com.loopers.domain.rank.RankPeriodType;
import com.loopers.ranking.RankingKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
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
    private static final String CLEANUP_STEP = "cleanupMonthlyRankStep";

    private final RankJobFactory rankJobFactory;

    @Bean(JOB_NAME)
    public Job monthlyRankJob(Step validateMonthlyScoreCompletenessStep,
                               Step buildMonthlyRankStep,
                               Step healthCheckMonthlyRankStep,
                               Step cleanupMonthlyRankStep) {
        return rankJobFactory.buildJob(JOB_NAME,
                validateMonthlyScoreCompletenessStep,
                buildMonthlyRankStep,
                healthCheckMonthlyRankStep,
                cleanupMonthlyRankStep);
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
    public Step healthCheckMonthlyRankStep(@Value("#{jobParameters['date']}") String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, RankJobFactory.DATE_FMT);
        return rankJobFactory.buildHealthCheckStep(
                HEALTH_CHECK_STEP,
                RankPeriodType.MONTHLY,
                RankingKeyGenerator.monthlyPeriodKey(date),
                RankingKeyGenerator.previousMonthlyPeriodKey(date)
        );
    }

    @Bean(CLEANUP_STEP)
    @JobScope
    public Step cleanupMonthlyRankStep(@Value("#{jobParameters['date']}") String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, RankJobFactory.DATE_FMT);
        return rankJobFactory.buildCleanupStep(
                CLEANUP_STEP,
                RankPeriodType.MONTHLY,
                RankingKeyGenerator.monthlyPeriodKey(date)
        );
    }
}
