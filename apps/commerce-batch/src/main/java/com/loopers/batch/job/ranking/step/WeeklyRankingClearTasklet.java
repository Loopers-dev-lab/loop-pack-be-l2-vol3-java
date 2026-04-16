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
import java.time.temporal.WeekFields;

@Slf4j
@StepScope
@Component
@RequiredArgsConstructor
public class WeeklyRankingClearTasklet implements Tasklet {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String yearWeek = toYearWeek(targetDate);
        log.info("[WeeklyRankingClearTasklet] 기존 데이터 삭제: yearWeek={}", yearWeek);

        int deleted = jdbcTemplate.update(
            "DELETE FROM mv_product_rank_weekly WHERE period_week = ?",
            yearWeek
        );
        log.info("[WeeklyRankingClearTasklet] 삭제 완료: {}건", deleted);
        return RepeatStatus.FINISHED;
    }

    public static String toYearWeek(String targetDate) {
        LocalDate date = LocalDate.parse(targetDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
        WeekFields weekFields = WeekFields.ISO;
        int week = date.get(weekFields.weekOfWeekBasedYear());
        int year = date.get(weekFields.weekBasedYear());
        return year + "-W" + String.format("%02d", week);
    }
}
