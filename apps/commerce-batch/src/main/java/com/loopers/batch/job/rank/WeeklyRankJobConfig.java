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

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankJobConfig {

    public static final String JOB_NAME = "weeklyRankJob";
    private static final String VALIDATION_STEP = "validateWeeklyScoreCompletenessStep";
    private static final String BUILD_STEP = "buildWeeklyRankStep";

    private final RankJobFactory rankJobFactory;

    @Bean(JOB_NAME)
    public Job weeklyRankJob(Step validateWeeklyScoreCompletenessStep, Step buildWeeklyRankStep) {
        return rankJobFactory.buildJob(JOB_NAME, validateWeeklyScoreCompletenessStep, buildWeeklyRankStep);
    }

    @Bean(VALIDATION_STEP)
    @JobScope
    public Step validateWeeklyScoreCompletenessStep(@Value("#{jobParameters['date']}") String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, RankJobFactory.DATE_FMT);
        return rankJobFactory.buildValidationStep(
                VALIDATION_STEP,
                RankingKeyGenerator.weekStart(date),
                RankingKeyGenerator.weekEnd(date)
        );
    }

    @Bean(BUILD_STEP)
    @JobScope
    public Step buildWeeklyRankStep(@Value("#{jobParameters['date']}") String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, RankJobFactory.DATE_FMT);
        return rankJobFactory.buildStep(
                BUILD_STEP,
                RankPeriodType.WEEKLY,
                RankingKeyGenerator.weeklyPeriodKey(date),
                RankingKeyGenerator.weekStart(date),
                RankingKeyGenerator.weekEnd(date)
        );
    }
}
