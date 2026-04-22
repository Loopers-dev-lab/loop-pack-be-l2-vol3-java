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
 * 월간 랭킹 집계 Writer. 구조는 Weekly와 동일, 경계만 월 단위.
 * 주→월 합산 금지 (독립 파생). 월은 항상 ranking_event 원천에서 재집계.
 */
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@Component
public class MonthlyRankingAggregationWriter
        implements ItemWriter<ScoredRankingEvent>, StepExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(MonthlyRankingAggregationWriter.class);
    private static final int TOP_N = 100;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Map<Long, Double> accumulator = new HashMap<>();
    private final String targetDate;

    public MonthlyRankingAggregationWriter(
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
        if (stepExecution.getStatus() != org.springframework.batch.core.BatchStatus.COMPLETED) {
            log.warn("[MonthlyRanking] Step failed — skip MV swap. status={}", stepExecution.getStatus());
            return stepExecution.getExitStatus();
        }

        LocalDate runDate = IsoPeriodMath.parseTargetDate(targetDate);
        String periodMonth = IsoPeriodMath.periodMonthOf(runDate);
        LocalDate periodStart = IsoPeriodMath.startOfMonth(runDate);
        LocalDate periodEnd = IsoPeriodMath.endOfMonth(runDate);
        boolean isFinalized = IsoPeriodMath.isPeriodFinalized(runDate, periodEnd);

        List<Map.Entry<Long, Double>> top = accumulator.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<Long, Double>>comparingDouble(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(TOP_N)
                .toList();

        log.info("[MonthlyRanking] aggregated={}, top={}, periodMonth={}, finalized={}",
                accumulator.size(), top.size(), periodMonth, isFinalized);

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    "DELETE FROM mv_product_rank_monthly WHERE period_month = ?",
                    periodMonth);

            if (top.isEmpty()) {
                return;
            }

            Timestamp now = Timestamp.from(ZonedDateTime.now(IsoPeriodMath.KST).toInstant());
            List<Object[]> batchArgs = new ArrayList<>(top.size());
            for (int i = 0; i < top.size(); i++) {
                Map.Entry<Long, Double> entry = top.get(i);
                batchArgs.add(new Object[]{
                        periodMonth,
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
                    INSERT INTO mv_product_rank_monthly
                        (period_month, rank_no, product_id, score,
                         period_start, period_end, is_finalized, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    batchArgs);
        });

        return ExitStatus.COMPLETED;
    }
}
