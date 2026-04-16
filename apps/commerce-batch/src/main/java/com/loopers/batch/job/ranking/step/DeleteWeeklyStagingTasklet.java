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
public class DeleteWeeklyStagingTasklet implements Tasklet {

    private static final String SQL =
        "DELETE FROM mv_product_rank_weekly_staging WHERE year_week = ?";

    private final JdbcTemplate jdbcTemplate;

    @Value("#{jobParameters['year_week']}")
    private String yearWeek;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // WeeklyJobParametersValidator 가 Job 레벨에서 null/blank 를 거르지만,
        // Tasklet 단위에서 한 번 더 방어하여 "validator 우회" 또는 "후속 리팩터링" 시의 parameter 바인딩 사고를 차단한다.
        Assert.hasText(yearWeek, "year_week must not be blank");
        int deleted = jdbcTemplate.update(SQL, yearWeek);
        log.info("[WeeklyRankingJob] cleared weekly staging rows. yearWeek={}, deleted={}",
            yearWeek, deleted);
        return RepeatStatus.FINISHED;
    }
}
