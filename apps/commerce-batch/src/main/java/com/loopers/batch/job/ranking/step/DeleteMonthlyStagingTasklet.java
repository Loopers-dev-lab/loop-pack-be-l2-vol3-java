package com.loopers.batch.job.ranking.step;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class DeleteMonthlyStagingTasklet implements Tasklet {

    private static final String SQL =
        "DELETE FROM mv_product_rank_monthly_staging WHERE year_month_key = ?";

    private final JdbcTemplate jdbcTemplate;

    @Value("#{jobParameters['year_month']}")
    private String yearMonth;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // MonthlyJobParametersValidator 가 Job 레벨에서 걸러주지만 Tasklet 단위 방어.
        Assert.hasText(yearMonth, "year_month must not be blank");
        int deleted = jdbcTemplate.update(SQL, yearMonth);
        log.info("[MonthlyRankingJob] cleared monthly staging rows. yearMonth={}, deleted={}",
            yearMonth, deleted);
        return RepeatStatus.FINISHED;
    }
}
