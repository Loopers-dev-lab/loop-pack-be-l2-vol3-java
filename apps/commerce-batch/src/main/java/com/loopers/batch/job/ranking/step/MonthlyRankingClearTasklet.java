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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@StepScope
@Component
@RequiredArgsConstructor
public class MonthlyRankingClearTasklet implements Tasklet {

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String yearMonth = toYearMonth(targetDate);
        log.info("[MonthlyRankingClearTasklet] 기존 데이터 삭제: yearMonth={}", yearMonth);

        int deleted = jdbcTemplate.update(
            "DELETE FROM mv_product_rank_monthly WHERE period_month = ?",
            yearMonth
        );
        log.info("[MonthlyRankingClearTasklet] 삭제 완료: {}건", deleted);
        return RepeatStatus.FINISHED;
    }

    public static String toYearMonth(String targetDate) {
        LocalDate date = LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
        return date.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }
}
