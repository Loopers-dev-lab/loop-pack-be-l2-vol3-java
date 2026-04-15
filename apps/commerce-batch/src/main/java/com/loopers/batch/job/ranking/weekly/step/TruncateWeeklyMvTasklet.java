package com.loopers.batch.job.ranking.weekly.step;

import com.loopers.batch.job.ranking.weekly.WeeklyRankingJobConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class TruncateWeeklyMvTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        jdbcTemplate.execute("DELETE FROM mv_product_rank_weekly");
        return RepeatStatus.FINISHED;
    }
}
