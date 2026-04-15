package com.loopers.batch.job.ranking;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 주간 랭킹 집계 Writer.
 *
 * 패턴: Reducing Writer + StepExecutionListener
 * - write(Chunk): 스트리밍 중 HashMap에 delta 누적만 수행 (DB 접근 없음)
 * - afterStep:    전 chunk 완료 후 TOP 100 계산 + 단일 트랜잭션 DELETE + INSERT
 *
 * 원자성 근거:
 * - Chunk 경계 = Spring Batch의 tx 경계. 중간 flush는 MV 일관성 훼손
 * - afterStep 독립 트랜잭션으로 한 번에 교체해야 원자 swap 달성
 *
 * tie-break: score DESC, productId ASC (DDL UNIQUE 제약과 일치)
 */
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@Component
public class WeeklyRankingAggregationWriter
        implements ItemWriter<ScoredRankingEvent>, StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(WeeklyRankingAggregationWriter.class);
    private static final int TOP_N = 100;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Map<Long, Double> accumulator = new HashMap<>();
    private final String targetDate;

    public WeeklyRankingAggregationWriter(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            @Value("#{jobParameters['targetDate']}") String targetDate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.targetDate = targetDate;
    }

    @Override
    public void write(@Nonnull Chunk<? extends ScoredRankingEvent> chunk) {
        for (ScoredRankingEvent event : chunk) {
            accumulator.merge(event.productId(), event.delta(), Double::sum);
        }
    }

    @Override
    public ExitStatus afterStep(@Nonnull StepExecution stepExecution) {
        LocalDate runDate = IsoPeriodMath.parseTargetDate(targetDate);
        String yearWeek = IsoPeriodMath.yearWeekOf(runDate);
        LocalDate periodStart = IsoPeriodMath.startOfIsoWeek(runDate);
        LocalDate periodEnd = IsoPeriodMath.endOfIsoWeek(runDate);
        boolean isFinalized = IsoPeriodMath.isPeriodFinalized(runDate, periodEnd);

        List<Map.Entry<Long, Double>> top = accumulator.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<Long, Double>>comparingDouble(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(TOP_N)
                .toList();

        log.info("[WeeklyRanking] aggregated={}, top={}, yearWeek={}, finalized={}",
                accumulator.size(), top.size(), yearWeek, isFinalized);

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "DELETE FROM mv_product_rank_weekly WHERE year_week = ?",
                    yearWeek);

            if (top.isEmpty()) {
                return;
            }

            Timestamp now = Timestamp.from(ZonedDateTime.now(IsoPeriodMath.KST).toInstant());
            List<Object[]> batchArgs = new ArrayList<>(top.size());
            for (int i = 0; i < top.size(); i++) {
                Map.Entry<Long, Double> entry = top.get(i);
                batchArgs.add(new Object[]{
                        yearWeek,
                        i + 1,
                        entry.getKey(),
                        entry.getValue(),
                        java.sql.Date.valueOf(periodStart),
                        java.sql.Date.valueOf(periodEnd),
                        isFinalized,
                        now
                });
            }

            jdbcTemplate.batchUpdate(
                    """
                    INSERT INTO mv_product_rank_weekly
                        (year_week, rank_no, product_id, score,
                         period_start, period_end, is_finalized, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    batchArgs);
        });

        return ExitStatus.COMPLETED;
    }
}
