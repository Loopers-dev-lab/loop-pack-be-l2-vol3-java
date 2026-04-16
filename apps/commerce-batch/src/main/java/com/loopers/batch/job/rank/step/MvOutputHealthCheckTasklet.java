package com.loopers.batch.job.rank.step;

import com.loopers.domain.rank.RankPeriodType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
public class MvOutputHealthCheckTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;
    private final RankPeriodType periodType;
    private final String currentPeriodKey;
    private final String previousPeriodKey;
    private final long minRows;
    private final double maxVariancePct;
    private final boolean failOnAnomaly;
    private final boolean backfillMode;

    public MvOutputHealthCheckTasklet(JdbcTemplate jdbcTemplate,
                                       RankPeriodType periodType,
                                       String currentPeriodKey,
                                       String previousPeriodKey,
                                       long minRows,
                                       double maxVariancePct,
                                       boolean failOnAnomaly) {
        this(jdbcTemplate, periodType, currentPeriodKey, previousPeriodKey,
                minRows, maxVariancePct, failOnAnomaly, false);
    }

    public MvOutputHealthCheckTasklet(JdbcTemplate jdbcTemplate,
                                       RankPeriodType periodType,
                                       String currentPeriodKey,
                                       String previousPeriodKey,
                                       long minRows,
                                       double maxVariancePct,
                                       boolean failOnAnomaly,
                                       boolean backfillMode) {
        this.jdbcTemplate = jdbcTemplate;
        this.periodType = periodType;
        this.currentPeriodKey = currentPeriodKey;
        this.previousPeriodKey = previousPeriodKey;
        this.minRows = minRows;
        this.maxVariancePct = maxVariancePct;
        this.failOnAnomaly = failOnAnomaly;
        this.backfillMode = backfillMode;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        long currentCount = countRows(currentPeriodKey);
        if (currentCount < minRows) {
            handleAnomaly(String.format("MV row 부족: type=%s periodKey=%s count=%d < min=%d",
                    periodType, currentPeriodKey, currentCount, minRows));
            return RepeatStatus.FINISHED;
        }

        if (backfillMode || previousPeriodKey == null) {
            log.info("MV health {}: type={} periodKey={} rows={}",
                    backfillMode ? "backfill-skip-variance" : "pass", periodType, currentPeriodKey, currentCount);
            return RepeatStatus.FINISHED;
        }

        long previousCount = countRows(previousPeriodKey);
        if (previousCount == 0L) {
            log.info("MV health: previous {} 비어있음 — variance skip", previousPeriodKey);
            return RepeatStatus.FINISHED;
        }
        double variance = Math.abs(currentCount - previousCount) / (double) previousCount;
        if (variance > maxVariancePct) {
            handleAnomaly(String.format(
                    "MV row 변동폭 초과: type=%s %s=%d %s=%d variance=%.2f%% > threshold=%.2f%%",
                    periodType, previousPeriodKey, previousCount, currentPeriodKey, currentCount,
                    variance * 100, maxVariancePct * 100));
            return RepeatStatus.FINISHED;
        }
        log.info("MV health ok: type={} prev={} curr={} variance={}%",
                periodType, previousCount, currentCount, String.format("%.2f", variance * 100));
        return RepeatStatus.FINISHED;
    }

    private long countRows(String periodKey) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + periodType.getTableName() + " WHERE period_key = ?",
                Long.class, periodKey
        );
        return n == null ? 0L : n;
    }

    private void handleAnomaly(String msg) {
        if (failOnAnomaly) {
            log.error(msg);
            throw new IllegalStateException(msg);
        }
        log.warn("{} — failOnAnomaly=false로 진행", msg);
    }
}
