package com.loopers.batch.job.ranking.weekly.step;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Step 0. 주간 집계 시작 전 {@code tmp_weekly_aggregate} 테이블을 초기화한다.
 *
 * <p>Step 1의 Writer가 ON DUPLICATE KEY UPDATE로 upsert하지만,
 * 이전 실행의 잔여물이 남아있으면 현재 실행 baseDate의 집계에 오염되므로 TRUNCATE.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeeklyCleanupTmpTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        jdbcTemplate.update("TRUNCATE TABLE tmp_weekly_aggregate");
        log.info("[WeeklyCleanupTmpTasklet] tmp_weekly_aggregate TRUNCATE 완료");
        return RepeatStatus.FINISHED;
    }
}
