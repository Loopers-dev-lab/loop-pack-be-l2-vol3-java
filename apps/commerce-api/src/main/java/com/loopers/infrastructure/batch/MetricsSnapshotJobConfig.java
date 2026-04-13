package com.loopers.infrastructure.batch;

import com.loopers.domain.metrics.ProductMetricsDaily;
import com.loopers.infrastructure.metrics.ProductMetricsDailyJpaRepository;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MetricsSnapshotJobConfig {

    private static final int CHUNK_SIZE = 100;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final ProductMetricsDailyJpaRepository productMetricsDailyJpaRepository;

    @Qualifier("mySqlMainDataSource")
    private final DataSource dataSource;

    record ProductMetricsRow(Long productId, long viewCount, long likeCount, long salesCount) {}

    @Bean
    public Job metricsSnapshotJob() {
        return new JobBuilder("metricsSnapshotJob", jobRepository)
                .start(metricsSnapshotStep())
                .build();
    }

    @Bean
    public Step metricsSnapshotStep() {
        return new StepBuilder("metricsSnapshotStep", jobRepository)
                .<ProductMetricsRow, ProductMetricsDaily>chunk(CHUNK_SIZE, transactionManager)
                .reader(productMetricsReader())
                .processor(productMetricsProcessor(null))
                .writer(productMetricsDailyWriter())
                .build();
    }

    @Bean
    public JdbcPagingItemReader<ProductMetricsRow> productMetricsReader() {
        MySqlPagingQueryProvider queryProvider = new MySqlPagingQueryProvider();
        queryProvider.setSelectClause("SELECT product_id, view_count, like_count, sales_count");
        queryProvider.setFromClause("FROM product_metrics");
        queryProvider.setSortKeys(Map.of("product_id", Order.ASCENDING));

        return new JdbcPagingItemReaderBuilder<ProductMetricsRow>()
                .name("productMetricsReader")
                .dataSource(dataSource)
                .queryProvider(queryProvider)
                .pageSize(CHUNK_SIZE)
                .rowMapper((rs, rowNum) -> new ProductMetricsRow(
                        rs.getLong("product_id"),
                        rs.getLong("view_count"),
                        rs.getLong("like_count"),
                        rs.getLong("sales_count")
                ))
                .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<ProductMetricsRow, ProductMetricsDaily> productMetricsProcessor(
            @Value("#{jobParameters['targetDate']}") String targetDateStr
    ) {
        return item -> {
            LocalDate targetDate = LocalDate.parse(targetDateStr, DATE_FORMATTER);
            LocalDate previousDate = targetDate.minusDays(1);

            Optional<ProductMetricsDaily> previousSnapshot =
                    productMetricsDailyJpaRepository.findByProductIdAndMetricDate(item.productId(), previousDate);

            long viewInc;
            long likeInc;
            long salesInc;

            if (previousSnapshot.isEmpty()) {
                // 첫 실행: 현재 누적값 그대로
                viewInc = item.viewCount();
                likeInc = item.likeCount();
                salesInc = item.salesCount();
            } else {
                ProductMetricsDaily prev = previousSnapshot.get();
                viewInc = item.viewCount() - prev.getViewCount();
                likeInc = item.likeCount() - prev.getLikeCount();
                salesInc = item.salesCount() - prev.getSalesCount();
            }

            // 음수 보정 (시스템 보정 등으로 감소 가능)
            viewInc = Math.max(0, viewInc);
            likeInc = Math.max(0, likeInc);
            salesInc = Math.max(0, salesInc);

            // 모두 0이면 스킵
            if (viewInc == 0 && likeInc == 0 && salesInc == 0) {
                return null;
            }

            return ProductMetricsDaily.of(item.productId(), targetDate, viewInc, likeInc, salesInc);
        };
    }

    @Bean
    public JpaItemWriter<ProductMetricsDaily> productMetricsDailyWriter() {
        return new JpaItemWriterBuilder<ProductMetricsDaily>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }
}
