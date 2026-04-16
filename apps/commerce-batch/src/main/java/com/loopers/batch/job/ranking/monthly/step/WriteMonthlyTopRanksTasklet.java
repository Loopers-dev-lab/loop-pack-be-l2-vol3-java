package com.loopers.batch.job.ranking.monthly.step;

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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * Step 2. {@code tmp_monthly_aggregate}에서 TOP 100을 뽑아
 * {@code mv_product_rank_monthly}에 단일 TX로 원자 교체한다.
 *
 * <p>year_month 포맷: {@code yyyy-MM} (baseDate의 년월).</p>
 */
@Component
@StepScope
@RequiredArgsConstructor
@Slf4j
public class WriteMonthlyTopRanksTasklet implements Tasklet {

    private static final int TOP_N = 100;

    private final JdbcTemplate jdbcTemplate;

    @Value("#{jobParameters['baseDate']}")
    private String baseDateParam;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate baseDate = LocalDate.parse(baseDateParam, DateTimeFormatter.BASIC_ISO_DATE);
        String yearMonth = YearMonth.from(baseDate).format(DateTimeFormatter.ofPattern("yyyy-MM"));

        int deleted = jdbcTemplate.update(
                "DELETE FROM mv_product_rank_monthly WHERE `year_month` = ?",
                yearMonth
        );

        int inserted = jdbcTemplate.update("""
                INSERT INTO mv_product_rank_monthly
                    (`year_month`, product_id, rank_no, score, like_count, order_count, view_count, order_amount, aggregated_at)
                SELECT
                    ? AS `year_month`,
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
                        FROM tmp_monthly_aggregate
                        ORDER BY score DESC, product_id ASC
                        LIMIT ?) t
                """, yearMonth, TOP_N);

        log.info("[WriteMonthlyTopRanksTasklet] yearMonth={}, deleted={}, inserted={}",
                yearMonth, deleted, inserted);
        return RepeatStatus.FINISHED;
    }
}
