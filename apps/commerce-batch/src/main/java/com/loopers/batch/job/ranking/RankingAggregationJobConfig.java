package com.loopers.batch.job.ranking;

import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.ProductRankSnapshot;
import com.loopers.domain.ranking.RankingType;
import com.loopers.infrastructure.ranking.ProductRankSnapshotJpaRepository;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDate;

@Slf4j
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingAggregationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class RankingAggregationJobConfig {
    public static final String JOB_NAME = "rankingAggregationJob";
    private static final String STEP_NAME = "aggregateRankingStep";
    private static final int CHUNK_SIZE = 100;

    private static final String READER_SQL = """
            SELECT *
            FROM (
                SELECT
                    pm.product_id,
                    p.name AS product_name,
                    p.price,
                    b.name AS brand_name,
                    SUM(pm.view_count)   AS total_view_count,
                    SUM(pm.like_count)   AS total_like_count,
                    SUM(pm.order_line_count) AS total_order_line_count,
                    SUM(pm.order_amount) AS total_order_amount,
                    (SUM(pm.view_count) * ? + SUM(pm.like_count) * ? + SUM(pm.order_amount) * ?) AS score,
                    ROW_NUMBER() OVER (
                        ORDER BY (SUM(pm.view_count) * ? + SUM(pm.like_count) * ? + SUM(pm.order_amount) * ?) DESC,
                                 pm.product_id ASC
                    ) AS rank_position
                FROM product_metrics_daily pm
                JOIN products p ON pm.product_id = p.id
                    AND p.deleted_at IS NULL
                    AND p.visibility = 'VISIBLE'
                JOIN brands b ON p.brand_id = b.id
                    AND b.deleted_at IS NULL
                WHERE pm.metric_date BETWEEN ? AND ?
                GROUP BY pm.product_id, p.name, p.price, b.name
            ) ranked
            WHERE rank_position <= 100
            """;

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ChunkListener chunkListener;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final ProductRankSnapshotJpaRepository productRankSnapshotJpaRepository;

    @Bean(JOB_NAME)
    public Job rankingAggregationJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                        .incrementer(new RunIdIncrementer())
                        .start(aggregateRankingStep(null))
                        .listener(jobListener)
                        .build();
    }

    @Bean(STEP_NAME)
    public Step aggregateRankingStep(JdbcCursorItemReader<ProductRankSnapshot> rankingAggregationReader) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<ProductRankSnapshot, ProductRankSnapshot>chunk(CHUNK_SIZE, transactionManager)
                .reader(rankingAggregationReader)
                .writer(rankingWriter())
                .listener(stepMonitorListener)
                .listener(chunkListener)
                .listener(deleteOldRankingStepListener(null, null))
                .build();
    }

    @StepScope
    @Bean
    public JdbcCursorItemReader<ProductRankSnapshot> rankingAggregationReader(
            DataSource dataSource,
            @Value("#{jobParameters['rankingType']}") String rankingTypeStr,
            @Value("#{jobParameters['endDate']}") LocalDate endDate,
            @Value("${ranking.weight.view:0.1}") double viewWeight,
            @Value("${ranking.weight.like:0.2}") double likeWeight,
            @Value("${ranking.weight.order:0.7}") double orderWeight
    ) {
        RankingType rankingType = RankingType.valueOf(rankingTypeStr);
        LocalDate actualEndDate = endDate != null ? endDate : LocalDate.now().minusDays(1);
        LocalDate startDate = actualEndDate.minusDays(rankingType.getDays() - 1);

        return new JdbcCursorItemReaderBuilder<ProductRankSnapshot>()
                .name("rankingAggregationReader")
                .dataSource(dataSource)
                .sql(READER_SQL)
                .preparedStatementSetter(ps -> {
                    ps.setDouble(1, viewWeight);
                    ps.setDouble(2, likeWeight);
                    ps.setDouble(3, orderWeight);
                    ps.setDouble(4, viewWeight);
                    ps.setDouble(5, likeWeight);
                    ps.setDouble(6, orderWeight);
                    ps.setObject(7, startDate);
                    ps.setObject(8, actualEndDate);
                })
                .rowMapper((rs, rowNum) -> ProductRankSnapshot.builder()
                        .rankingType(rankingType)
                        .rankDate(actualEndDate)
                        .rankPosition(rs.getInt("rank_position"))
                        .productId(rs.getLong("product_id"))
                        .productName(rs.getString("product_name"))
                        .price(rs.getInt("price"))
                        .brandName(rs.getString("brand_name"))
                        .totalViewCount(rs.getLong("total_view_count"))
                        .totalLikeCount(rs.getLong("total_like_count"))
                        .totalOrderLineCount(rs.getLong("total_order_line_count"))
                        .totalOrderAmount(rs.getLong("total_order_amount"))
                        .score(rs.getDouble("score"))
                        .build())
                .build();
    }

    @StepScope
    @Bean
    public StepExecutionListener deleteOldRankingStepListener(
            @Value("#{jobParameters['rankingType']}") String rankingTypeStr,
            @Value("#{jobParameters['endDate']}") LocalDate endDate
    ) {
        return new StepExecutionListener() {
            @Override
            @Transactional
            public void beforeStep(StepExecution stepExecution) {
                RankingType rankingType = RankingType.valueOf(rankingTypeStr);
                LocalDate actualEndDate = endDate != null ? endDate : LocalDate.now().minusDays(1);
                log.info("기존 랭킹 데이터 삭제: rankingType={}, rankDate={}", rankingType, actualEndDate);
                productRankSnapshotJpaRepository.deleteByRankingTypeAndRankDate(rankingType, actualEndDate);
            }

            @Override
            public ExitStatus afterStep(StepExecution stepExecution) {
                return stepExecution.getExitStatus();
            }
        };
    }

    @Bean
    public JpaItemWriter<ProductRankSnapshot> rankingWriter() {
        return new JpaItemWriterBuilder<ProductRankSnapshot>()
                .entityManagerFactory(entityManagerFactory)
                .usePersist(true)
                .build();
    }
}
