package com.loopers.batch.job.rank.step;

import com.loopers.domain.rank.RankPeriodType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@Slf4j
public class MvRankCleanupTasklet implements Tasklet {

    private final JdbcTemplate jdbcTemplate;
    private final RankPeriodType periodType;
    private final String periodKey;
    private final int batchLimit;
    private final MeterRegistry meterRegistry;

    public MvRankCleanupTasklet(JdbcTemplate jdbcTemplate,
                                 RankPeriodType periodType,
                                 String periodKey,
                                 int batchLimit) {
        this(jdbcTemplate, periodType, periodKey, batchLimit, null);
    }

    public MvRankCleanupTasklet(JdbcTemplate jdbcTemplate,
                                 RankPeriodType periodType,
                                 String periodKey,
                                 int batchLimit,
                                 MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.periodType = periodType;
        this.periodKey = periodKey;
        this.batchLimit = batchLimit;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        List<Long> publishedVersions = jdbcTemplate.queryForList(
                "SELECT published_version FROM mv_product_rank_publication WHERE period_type = ? AND period_key = ?",
                Long.class, periodType.name(), periodKey
        );
        Long publishedVersion = publishedVersions.isEmpty() ? null : publishedVersions.get(0);
        if (publishedVersion == null || publishedVersion <= 0L) {
            log.info("MV cleanup 스킵: type={}, periodKey={} — published_version 부재",
                    periodType, periodKey);
            return RepeatStatus.FINISHED;
        }

        Timer.Sample sample = meterRegistry == null ? null : Timer.start(meterRegistry);
        long totalDeleted = 0L;
        int deleted;
        do {
            deleted = jdbcTemplate.update(
                    "DELETE FROM " + periodType.getTableName() +
                            " WHERE period_key = ? AND version < ? LIMIT ?",
                    periodKey, publishedVersion, batchLimit
            );
            totalDeleted += deleted;
        } while (deleted == batchLimit);

        if (sample != null) {
            sample.stop(Timer.builder("batch.rank.cleanup")
                    .tag("period_type", periodType.name())
                    .register(meterRegistry));
            meterRegistry.counter("batch.rank.cleanup.deleted",
                    "period_type", periodType.name()).increment(totalDeleted);
        }

        log.info("MV cleanup 완료: type={}, periodKey={}, publishedVersion={}, deleted={}",
                periodType, periodKey, publishedVersion, totalDeleted);
        return RepeatStatus.FINISHED;
    }
}
