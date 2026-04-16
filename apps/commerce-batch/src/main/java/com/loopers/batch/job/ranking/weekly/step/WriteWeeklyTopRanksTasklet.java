package com.loopers.batch.job.ranking.weekly.step;

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

/**
 * Step 2. {@code tmp_weekly_aggregate}에서 TOP 100을 뽑아
 * {@code mv_product_rank_weekly}에 단일 TX로 원자 교체한다.
 *
 * <p>전략: DELETE (base_date) → INSERT…SELECT (tmp ORDER BY score DESC LIMIT 100).
 * 재실행 시 같은 baseDate 기준으로 이전 결과를 지우고 새로 넣으므로 멱등.</p>
 */
@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class WriteWeeklyTopRanksTasklet implements Tasklet {

    private static final int TOP_N = 100;

    private final JdbcTemplate jdbcTemplate;

    @Value("#{jobParameters['baseDate']}")
    private String baseDateParam;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate baseDate = LocalDate.parse(baseDateParam, DateTimeFormatter.BASIC_ISO_DATE);

        int deleted = jdbcTemplate.update(
                "DELETE FROM mv_product_rank_weekly WHERE base_date = ?",
                baseDate
        );

        int inserted = jdbcTemplate.update("""
                INSERT INTO mv_product_rank_weekly
                    (base_date, product_id, rank_no, score, like_count, order_count, view_count, order_amount, aggregated_at)
                SELECT
                    ? AS base_date,
                    t.product_id,
                    (@rn := @rn + 1) AS rank_no,
                    t.score,
                    t.like_count,
                    t.order_count,
                    t.view_count,
                    t.order_amount,
                    NOW() AS aggregated_at
                FROM (SELECT @rn := 0) r,
                     (SELECT product_id, score, like_count, order_count, view_count, order_amount
                        FROM tmp_weekly_aggregate
                        ORDER BY score DESC, product_id ASC
                        LIMIT ?) t
                """, baseDate, TOP_N);

        log.info("[WriteWeeklyTopRanksTasklet] baseDate={}, deleted={}, inserted={}",
                baseDate, deleted, inserted);
        return RepeatStatus.FINISHED;
    }
}
