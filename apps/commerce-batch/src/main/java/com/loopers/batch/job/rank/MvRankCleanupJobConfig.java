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

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MvRankCleanupJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MvRankCleanupJobConfig {

    public static final String JOB_NAME = "mvRankCleanupJob";
    private static final String WEEKLY_STEP = "cleanupWeeklyMvStep";
    private static final String MONTHLY_STEP = "cleanupMonthlyMvStep";
    private static final String QUARTERLY_STEP = "cleanupQuarterlyMvStep";

    private final RankJobFactory rankJobFactory;
    private final JobRepository jobRepository;

    @Bean(JOB_NAME)
    public Job mvRankCleanupJob(Step cleanupWeeklyMvStep, Step cleanupMonthlyMvStep, Step cleanupQuarterlyMvStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(cleanupWeeklyMvStep)
                .next(cleanupMonthlyMvStep)
                .next(cleanupQuarterlyMvStep)
                .build();
    }

    @Bean(WEEKLY_STEP)
    @JobScope
    public Step cleanupWeeklyMvStep(@Value("#{jobParameters['date']}") String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, RankJobFactory.DATE_FMT);
        return rankJobFactory.buildCleanupStep(
                WEEKLY_STEP,
                RankPeriodType.WEEKLY,
                RankingKeyGenerator.weeklyPeriodKey(date)
        );
    }

    @Bean(MONTHLY_STEP)
    @JobScope
    public Step cleanupMonthlyMvStep(@Value("#{jobParameters['date']}") String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, RankJobFactory.DATE_FMT);
        return rankJobFactory.buildCleanupStep(
                MONTHLY_STEP,
                RankPeriodType.MONTHLY,
                RankingKeyGenerator.monthlyPeriodKey(date)
        );
    }

    @Bean(QUARTERLY_STEP)
    @JobScope
    public Step cleanupQuarterlyMvStep(@Value("#{jobParameters['date']}") String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, RankJobFactory.DATE_FMT);
        return rankJobFactory.buildCleanupStep(
                QUARTERLY_STEP,
                RankPeriodType.QUARTERLY,
                RankingKeyGenerator.quarterlyPeriodKey(date)
        );
    }
}
