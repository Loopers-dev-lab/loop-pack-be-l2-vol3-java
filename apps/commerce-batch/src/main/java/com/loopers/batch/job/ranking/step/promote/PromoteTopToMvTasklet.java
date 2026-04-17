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
 * Step 5b — MV 의 해당 anchor 를 DELETE 한 뒤 2차 스테이징에서 TOP 100 을 INSERT.
 *
 * <p>DELETE + INSERT 가 **단일 TX** 안에서 실행되므로 READ COMMITTED 에서
 * 외부 세션(API) 은 커밋 전까지 이전 MV 를, 커밋 후에는 새 MV 만 봄.
 * "MV 가 비어있는 순간" 이 물리적으로 노출되지 않는다 (원자 교체).</p>
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class PromoteTopToMvTasklet implements Tasklet {

    public static final int TOP_N = 100;
    private static final DateTimeFormatter KEY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String INSERT_SQL_LAST_7D  = insertSqlFor("mv_product_rank_last_7d");
    private static final String INSERT_SQL_LAST_30D = insertSqlFor("mv_product_rank_last_30d");

    private final JdbcTemplate jdbcTemplate;

    @Value("#{jobExecutionContext['" + RankingJobParametersListener.CTX_ANCHOR_DATE_KEY + "']}")
    private String anchorDateKey;

    @Override
    @Transactional
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        LocalDate anchorDate = LocalDate.parse(anchorDateKey, KEY_FORMAT);
        Date sqlDate = Date.valueOf(anchorDate);
        List<WeightConfig> configs = RankingJobParametersListener.restoreWeightConfigs(
                chunkContext.getStepContext().getStepExecution().getJobExecution().getExecutionContext());

        // 1. DELETE — 이전 MV 제거 (같은 TX 안이라 외부에 아직 안 보임)
        int deleted7d  = jdbcTemplate.update(
                "DELETE FROM mv_product_rank_last_7d WHERE anchor_date = ?", sqlDate);
        int deleted30d = jdbcTemplate.update(
                "DELETE FROM mv_product_rank_last_30d WHERE anchor_date = ?", sqlDate);

        // 2. INSERT — TOP 100 적재
        Timestamp createdAt = Timestamp.valueOf(LocalDateTime.now());
        int totalInserted = 0;
        for (WeightConfig config : configs) {
            totalInserted += promote(INSERT_SQL_LAST_7D,  StagingAggregationProcessor.PERIOD_LAST_7D,
                                     anchorDate, config.getGroupName(), createdAt);
            totalInserted += promote(INSERT_SQL_LAST_30D, StagingAggregationProcessor.PERIOD_LAST_30D,
                                     anchorDate, config.getGroupName(), createdAt);
        }

        log.info("[STEP=promoteTopToMvStep] anchorDate={} deleted7d={} deleted30d={} inserted={}",
                anchorDate, deleted7d, deleted30d, totalInserted);

        contribution.incrementWriteCount(deleted7d + deleted30d + totalInserted);
        return RepeatStatus.FINISHED;
    }

    private int promote(String sql, String periodType, LocalDate anchorDate,
                        String weightGroup, Timestamp createdAt) {
        return jdbcTemplate.update(
                sql,
                periodType, anchorDateKey, weightGroup,
                Date.valueOf(anchorDate), createdAt,
                TOP_N
        );
    }

    private static String insertSqlFor(String mvTable) {
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
