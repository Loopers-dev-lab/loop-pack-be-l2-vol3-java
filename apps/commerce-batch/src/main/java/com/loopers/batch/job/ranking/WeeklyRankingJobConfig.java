package com.loopers.batch.job.ranking;

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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final String STEP_NAME = "weeklyRankingStep";
    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob(Step weeklyRankingStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(weeklyRankingStep)
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean(STEP_NAME)
    public Step weeklyRankingStep(
            PlatformTransactionManager txManager,
            JdbcCursorItemReader<RankingScoreRow> weeklyReader,
            ItemWriter<RankingScoreRow> weeklyWriter
    ) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<RankingScoreRow, RankingScoreRow>chunk(CHUNK_SIZE, txManager)
                .reader(weeklyReader)
                .writer(weeklyWriter)
                .listener(stepMonitorListener)
                .build();
    }

    @StepScope
    @Bean
    public JdbcCursorItemReader<RankingScoreRow> weeklyReader(
            DataSource dataSource,
            @Value("#{jobParameters['targetDate']}") LocalDate targetDate
    ) {
        LocalDate monday = targetDate.with(DayOfWeek.MONDAY);
        LocalDate sunday = targetDate.with(DayOfWeek.SUNDAY);

        return new JdbcCursorItemReaderBuilder<RankingScoreRow>()
                .name("weeklyMetricsReader")
                .dataSource(dataSource)
                .sql("""
                        SELECT
                            sub.product_id,
                            sub.total_views,
                            sub.total_likes,
                            sub.total_amount,
                            sub.score,
                            ROW_NUMBER() OVER (ORDER BY sub.score DESC) AS ranking
                        FROM (
                            SELECT
                                product_id,
                                SUM(view_count) AS total_views,
                                SUM(like_count) AS total_likes,
                                SUM(order_amount) AS total_amount,
                                (SUM(view_count) * 0.1 + SUM(like_count) * 0.2 + LOG(1 + SUM(order_amount)) * 0.7) AS score
                            FROM product_daily_metrics
                            WHERE metric_date BETWEEN ? AND ?
                            GROUP BY product_id
                            ORDER BY score DESC
                            LIMIT 100
                        ) sub
                        """)
                .preparedStatementSetter(ps -> {
                    ps.setDate(1, Date.valueOf(monday));
                    ps.setDate(2, Date.valueOf(sunday));
                })
                .rowMapper((rs, rowNum) -> new RankingScoreRow(
                        rs.getLong("product_id"),
                        rs.getLong("total_views"),
                        rs.getLong("total_likes"),
                        rs.getLong("total_amount"),
                        rs.getDouble("score"),
                        rs.getInt("ranking")
                ))
                .build();
    }

    @StepScope
    @Bean
    public ItemWriter<RankingScoreRow> weeklyWriter(
            NamedParameterJdbcTemplate jdbcTemplate,
            @Value("#{jobParameters['targetDate']}") LocalDate targetDate
    ) {
        int year = targetDate.get(IsoFields.WEEK_BASED_YEAR);
        int week = targetDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        String yearWeek = String.format("%d-W%02d", year, week);

        return items -> {
            String sql = """
                    INSERT INTO mv_product_rank_weekly
                        (product_id, ranking_week, view_count, like_count, order_amount, score, ranking, updated_at)
                    VALUES
                        (:productId, :yearWeek, :viewCount, :likeCount, :orderAmount, :score, :ranking, :updatedAt)
                    ON DUPLICATE KEY UPDATE
                        view_count = VALUES(view_count),
                        like_count = VALUES(like_count),
                        order_amount = VALUES(order_amount),
                        score = VALUES(score),
                        ranking = VALUES(ranking),
                        updated_at = VALUES(updated_at)
                    """;

            SqlParameterSource[] batchParams = items.getItems().stream()
                    .map(row -> new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                            .addValue("productId", row.productId())
                            .addValue("yearWeek", yearWeek)
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
