package com.loopers.batch.job.ranking.step.promote;

import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.batch.job.ranking.step.stage.StagingAggregationProcessor;
import com.loopers.domain.ranking.weight.WeightConfig;
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
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Step 5b — 2차 스테이징(staging_ranking_scored) 에서 TOP 100 만 MV 에 INSERT.
 *
 * <p>(period_type × weight_group) 조합 당 한 번의 단일 SQL:
 * {@code INSERT INTO mv SELECT ... ROW_NUMBER() OVER (ORDER BY score DESC) ... LIMIT 100}.
 * Step 4a/4b 가 사전 DELETE 했으므로 MV 는 "비어있음 → 확정된 TOP 100" 두 상태만 통과한다.</p>
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class PromoteTopToMvTasklet implements Tasklet {

    public static final int TOP_N = 100;
    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 각 period_type 별로 MV 테이블 이름이 다름
    private static final String SQL_LAST_7D = sqlFor("mv_product_rank_last_7d");
    private static final String SQL_LAST_30D = sqlFor("mv_product_rank_last_30d");

    private final JdbcTemplate jdbcTemplate;

    @Value("#{jobExecutionContext['" + RankingJobParametersListener.CTX_ANCHOR_DATE_KEY + "']}")
    private String anchorDateKey;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate anchorDate = LocalDate.parse(anchorDateKey, KEY_FORMAT);
        List<WeightConfig> configs = RankingJobParametersListener.restoreWeightConfigs(
                chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext());

        Timestamp createdAt = Timestamp.valueOf(LocalDateTime.now());
        int totalInserted = 0;

        for (WeightConfig config : configs) {
            totalInserted += promote(SQL_LAST_7D,  StagingAggregationProcessor.PERIOD_LAST_7D,
                                     anchorDate, config.getGroupName(), createdAt);
            totalInserted += promote(SQL_LAST_30D, StagingAggregationProcessor.PERIOD_LAST_30D,
                                     anchorDate, config.getGroupName(), createdAt);
        }

        log.info("[STEP=promoteTopToMvStep] anchorDate={} groups={} inserted={}",
                anchorDate, configs.size(), totalInserted);

        contribution.incrementWriteCount(totalInserted);
        return RepeatStatus.FINISHED;
    }

    private int promote(String sql, String periodType, LocalDate anchorDate,
                        String weightGroup, Timestamp createdAt) {
        // SQL 의 ? 출현 순서: period_type, period_key, weight_group (CTE WHERE)
        //                    anchor_date, created_at (SELECT literal)
        //                    top_n (WHERE rn <= ?)
        return jdbcTemplate.update(
                sql,
                periodType, anchorDateKey, weightGroup,
                Date.valueOf(anchorDate), createdAt,
                TOP_N
        );
    }

    private static String sqlFor(String mvTable) {
        return """
                INSERT INTO %s
                    (anchor_date, weight_group, product_id,
                     view_count, like_count, sales_amount,
                     score, rank_position, created_at)
                WITH ranked AS (
                    SELECT weight_group, product_id,
                           view_count, like_count, sales_amount, score,
                           ROW_NUMBER() OVER (ORDER BY score DESC, product_id ASC) AS rn
                      FROM staging_ranking_scored
                     WHERE period_type  = ?
                       AND period_key   = ?
                       AND weight_group = ?
                )
                SELECT ?, weight_group, product_id,
                       view_count, like_count, sales_amount, score, rn, ?
                  FROM ranked
                 WHERE rn <= ?
                """.formatted(mvTable);
    }
}
