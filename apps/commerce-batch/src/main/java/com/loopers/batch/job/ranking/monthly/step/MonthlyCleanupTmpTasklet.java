package com.loopers.batch.job.ranking.monthly.step;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Step 0. 월간 집계 시작 전 {@code tmp_monthly_aggregate} TRUNCATE.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MonthlyCleanupTmpTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        jdbcTemplate.update("TRUNCATE TABLE tmp_monthly_aggregate");
        log.info("[MonthlyCleanupTmpTasklet] tmp_monthly_aggregate TRUNCATE 완료");
        return RepeatStatus.FINISHED;
    }
}
