package com.loopers.batch.job.rankingmv.step;

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
@RequiredArgsConstructor
@Component
public class CleanupTasklet implements Tasklet {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int RETENTION_DAYS = 3;

    private final JdbcTemplate jdbcTemplate;

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Value("#{jobParameters['scope']}")
    private String scope;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        String mvTable = resolveMvTable(scope);

        int deletedMv = jdbcTemplate.update(
            "DELETE FROM " + mvTable + " WHERE period_key = ?", targetDate);
        log.info("[Cleanup] {} 삭제: period_key={}, rows={}", mvTable, targetDate, deletedMv);

        int deletedStaging = jdbcTemplate.update(
            "DELETE FROM mv_product_rank_staging WHERE period_key = ?", targetDate);
        log.info("[Cleanup] staging 삭제: period_key={}, rows={}", targetDate, deletedStaging);

        LocalDate cutoffDate = LocalDate.parse(targetDate, DATE_FORMATTER).minusDays(RETENTION_DAYS);
        String cutoffKey = cutoffDate.format(DATE_FORMATTER);

        int purgedMv = jdbcTemplate.update(
            "DELETE FROM " + mvTable + " WHERE period_key < ?", cutoffKey);
        int purgedStaging = jdbcTemplate.update(
            "DELETE FROM mv_product_rank_staging WHERE period_key < ?", cutoffKey);

        if (purgedMv + purgedStaging > 0) {
            log.info("[Cleanup] {}일 이전 데이터 정리: mv={}, staging={}", RETENTION_DAYS, purgedMv, purgedStaging);
        }

        return RepeatStatus.FINISHED;
    }

    private String resolveMvTable(String scope) {
        return switch (scope) {
            case "weekly" -> "mv_product_rank_weekly";
            case "monthly" -> "mv_product_rank_monthly";
            default -> throw new IllegalArgumentException("Invalid scope: " + scope);
        };
    }
}
