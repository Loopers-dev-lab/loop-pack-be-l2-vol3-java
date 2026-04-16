package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.step.AggregatedMetricRow;
import com.loopers.batch.job.ranking.step.RankingScoreProcessor;
import com.loopers.batch.job.ranking.step.RankingScoreRow;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";
    private static final String STEP_NAME = "monthlyRankingStep";
    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob(Step monthlyRankingStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(monthlyRankingStep)
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean(STEP_NAME)
    public Step monthlyRankingStep(
            PlatformTransactionManager txManager,
            JdbcCursorItemReader<AggregatedMetricRow> monthlyReader,
            ItemProcessor<AggregatedMetricRow, RankingScoreRow> monthlyProcessor,
            ItemWriter<RankingScoreRow> monthlyWriter
    ) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<AggregatedMetricRow, RankingScoreRow>chunk(CHUNK_SIZE, txManager)
                .reader(monthlyReader)
                .processor(monthlyProcessor)
                .writer(monthlyWriter)
                .listener(stepMonitorListener)
                .build();
    }

    @StepScope
    @Bean
    public JdbcCursorItemReader<AggregatedMetricRow> monthlyReader(
            DataSource dataSource,
            @Value("#{jobParameters['targetDate']}") LocalDate targetDate
    ) {
        LocalDate firstDay = targetDate.withDayOfMonth(1);
        LocalDate lastDay = targetDate.with(TemporalAdjusters.lastDayOfMonth());

        return new JdbcCursorItemReaderBuilder<AggregatedMetricRow>()
                .name("monthlyMetricsReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT
                            product_id,
                            SUM(view_count)  AS total_views,
                            SUM(like_count)  AS total_likes,
                            SUM(order_amount) AS total_amount
                        FROM product_daily_metrics
                        WHERE metric_date BETWEEN ? AND ?
                        GROUP BY product_id
                        ORDER BY (SUM(view_count) * 0.1 + SUM(like_count) * 0.2 + LOG(1 + SUM(order_amount)) * 0.7) DESC, product_id ASC
                        LIMIT 100
                        """)
                .preparedStatementSetter(ps -> {
                    ps.setDate(1, Date.valueOf(firstDay));
                    ps.setDate(2, Date.valueOf(lastDay));
                })
                .rowMapper((rs, rowNum) -> new AggregatedMetricRow(
                        rs.getLong("product_id"),
                        rs.getLong("total_views"),
                        rs.getLong("total_likes"),
                        rs.getLong("total_amount")
                ))
                .build();
    }

    @StepScope
    @Bean
    public ItemProcessor<AggregatedMetricRow, RankingScoreRow> monthlyProcessor() {
        return new RankingScoreProcessor();
    }

    @StepScope
    @Bean
    public ItemWriter<RankingScoreRow> monthlyWriter(
            NamedParameterJdbcTemplate jdbcTemplate,
            @Value("#{jobParameters['targetDate']}") LocalDate targetDate
    ) {
        String yearMonth = String.format("%d-%02d", targetDate.getYear(), targetDate.getMonthValue());

        return items -> {
            jdbcTemplate.update(
                    "DELETE FROM mv_product_rank_monthly WHERE ranking_month = :yearMonth",
                    new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("yearMonth", yearMonth)
            );

            String sql = """
                    INSERT INTO mv_product_rank_monthly
                        (product_id, ranking_month, view_count, like_count, order_amount, score, ranking, updated_at)
                    VALUES
                        (:productId, :yearMonth, :viewCount, :likeCount, :orderAmount, :score, :ranking, :updatedAt)
                    """;

            SqlParameterSource[] batchParams = items.getItems().stream()
                    .map(row -> new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                            .addValue("productId", row.productId())
                            .addValue("yearMonth", yearMonth)
                            .addValue("viewCount", row.viewCount())
                            .addValue("likeCount", row.likeCount())
                            .addValue("orderAmount", row.orderAmount())
                            .addValue("score", row.score())
                            .addValue("ranking", row.ranking())
                            .addValue("updatedAt", ZonedDateTime.now()))
                    .toArray(SqlParameterSource[]::new);
            jdbcTemplate.batchUpdate(sql, batchParams);
        };
    }
}
