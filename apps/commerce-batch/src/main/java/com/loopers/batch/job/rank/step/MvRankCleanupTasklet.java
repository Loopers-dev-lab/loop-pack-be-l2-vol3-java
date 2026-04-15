package com.loopers.batch.job.rank.step;

import com.loopers.domain.rank.RankPeriodType;
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

    public MvRankCleanupTasklet(JdbcTemplate jdbcTemplate,
                                 RankPeriodType periodType,
                                 String periodKey,
                                 int batchLimit) {
        this.jdbcTemplate = jdbcTemplate;
        this.periodType = periodType;
        this.periodKey = periodKey;
        this.batchLimit = batchLimit;
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

        log.info("MV cleanup 완료: type={}, periodKey={}, publishedVersion={}, deleted={}",
                periodType, periodKey, publishedVersion, totalDeleted);
        return RepeatStatus.FINISHED;
    }
}
