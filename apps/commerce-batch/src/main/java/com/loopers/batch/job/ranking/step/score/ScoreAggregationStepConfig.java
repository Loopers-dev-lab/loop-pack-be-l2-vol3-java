package com.loopers.batch.job.ranking.step.score;

import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.staging.StagingRankingAggregation;
import com.loopers.domain.ranking.staging.StagingRankingScored;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class ScoreAggregationStepConfig {

    public static final String STEP_NAME = "scoreAggregationStep";
    private static final int CHUNK_SIZE = 500;
    private static final int FETCH_SIZE = 2000;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final StepMonitorListener stepMonitorListener;
    private final ScoreProcessor scoreProcessor;
    private final StagingScoredWriter stagingScoredWriter;
    private final DataSource dataSource;

    @Bean
    @StepScope
    public JdbcCursorItemReader<StagingRankingAggregation> stagingAggregationCursorReader(
            @Value("#{jobExecutionContext['" + RankingJobParametersListener.CTX_ANCHOR_DATE_KEY + "']}") String anchorDateKey
    ) {
        return new JdbcCursorItemReaderBuilder<StagingRankingAggregation>()
                .name("stagingAggregationCursorReader")
                .dataSource(dataSource)
                .fetchSize(FETCH_SIZE)
                // saveState=false: 2차 staging 도 UPSERT 라 멱등. restart 시 처음부터.
                .saveState(false)
                .sql("""
                        SELECT period_type, period_key, product_id,
                               view_count, like_count, sales_amount
                          FROM staging_ranking_aggregation
                         WHERE period_key = ?
                         ORDER BY period_type, product_id
                        """)
                .preparedStatementSetter((ps) -> ps.setString(1, anchorDateKey))
                .rowMapper((rs, rowNum) -> new StagingRankingAggregation(
                        rs.getString("period_type"),
                        rs.getString("period_key"),
                        rs.getLong("product_id"),
                        rs.getLong("view_count"),
                        rs.getLong("like_count"),
                        rs.getLong("sales_amount")
                ))
                .build();
    }

    @Bean(STEP_NAME)
    public Step scoreAggregationStep(JdbcCursorItemReader<StagingRankingAggregation> stagingAggregationCursorReader) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<StagingRankingAggregation, List<StagingRankingScored>>chunk(CHUNK_SIZE, transactionManager)
                .reader(stagingAggregationCursorReader)
                .processor(scoreProcessor)
                .writer(stagingScoredWriter)
                .listener(stepMonitorListener)
                .build();
    }
}
