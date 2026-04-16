package com.loopers.batch.job.ranking.monthly.step;

import com.loopers.batch.job.ranking.monthly.MonthlyRankingJobConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class TruncateMonthlyMvTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;

    private LocalDate baseDate;

    @Value("#{jobParameters['targetYearMonth']}")
    public void setTargetYearMonth(String targetYearMonth) {
        this.baseDate = YearMonth.parse(targetYearMonth, DateTimeFormatter.ofPattern("yyyyMM")).atDay(1);
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly WHERE base_date = ?", baseDate);
        return RepeatStatus.FINISHED;
    }
}
