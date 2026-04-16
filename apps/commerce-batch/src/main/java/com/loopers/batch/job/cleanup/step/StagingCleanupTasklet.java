package com.loopers.batch.job.cleanup.step;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;

@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class StagingCleanupTasklet implements Tasklet {

    static final int WEEKLY_RETENTION_WEEKS = 4;
    static final int MONTHLY_RETENTION_MONTHS = 3;
    // "오늘" 을 ISO 주차/월로 변환할 때 JVM 기본 TZ 에 의존하지 않도록 KST 를 명시.
    // 다른 스케줄/Listener 가 모두 Asia/Seoul 기준으로 동작하는 것과 일관성을 맞춘다.
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final String DELETE_WEEKLY_SQL =
        "DELETE FROM mv_product_rank_weekly_staging WHERE year_week < ?";

    private static final String DELETE_MONTHLY_SQL =
        "DELETE FROM mv_product_rank_monthly_staging WHERE year_month_key < ?";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate today = LocalDate.now(KST);

        String weeklyThreshold = computeWeeklyThreshold(today);
        int weeklyDeleted = jdbcTemplate.update(DELETE_WEEKLY_SQL, weeklyThreshold);

        String monthlyThreshold = computeMonthlyThreshold(today);
        int monthlyDeleted = jdbcTemplate.update(DELETE_MONTHLY_SQL, monthlyThreshold);

        log.info(
            "[StagingCleanupJob] cleaned staging. weeklyThreshold={}, weeklyDeleted={}, "
                + "monthlyThreshold={}, monthlyDeleted={}",
            weeklyThreshold, weeklyDeleted, monthlyThreshold, monthlyDeleted);
        return RepeatStatus.FINISHED;
    }

    static String computeWeeklyThreshold(LocalDate today) {
        // 현재 주 포함 WEEKLY_RETENTION_WEEKS 주만 보존 (따라서 RETENTION-1 만큼 과거 지점을 cutoff 로).
        LocalDate cutoff = today.minusWeeks(WEEKLY_RETENTION_WEEKS - 1L);
        int year = cutoff.get(IsoFields.WEEK_BASED_YEAR);
        int week = cutoff.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%04d-W%02d", year, week);
    }

    static String computeMonthlyThreshold(LocalDate today) {
        LocalDate cutoff = today.minusMonths(MONTHLY_RETENTION_MONTHS - 1L);
        return String.format("%04d-%02d", cutoff.getYear(), cutoff.getMonthValue());
    }
}
