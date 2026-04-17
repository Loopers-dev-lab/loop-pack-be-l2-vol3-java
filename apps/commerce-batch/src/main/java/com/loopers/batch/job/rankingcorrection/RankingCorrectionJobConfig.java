package com.loopers.batch.job.rankingcorrection;

import com.loopers.domain.ranking.ScoreFormula;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Lambda Architecture 배치 보정 잡.
 *
 * <p>DB 원장(product_metrics) 기준으로 Redis 랭킹(Hash + ZSET)을 덮어쓴다.
 * 실시간 경로(Kafka → Redis)에서 누적된 드리프트를 1시간 주기로 보정.</p>
 *
 * @see ScoreFormula
 */
@Slf4j
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingCorrectionJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class RankingCorrectionJobConfig {

    public static final String JOB_NAME = "rankingCorrectionJob";
    private static final String STEP_NAME = "rankingCorrectionStep";
    private static final int CHUNK_SIZE = 1_000;

    // RankingScoreUpdater와 동일한 Semantic Definition
    private static final String RANKING_ZSET_PREFIX = "ranking:all:";
    private static final String RANKING_METRICS_PREFIX = "ranking:metrics:";
    private static final long RANKING_ZSET_TTL_SECONDS = 691_200L; // 8일
    private static final long RANKING_HASH_TTL_SECONDS = 172_800L; // 2일
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final RankingCorrectionProperties properties;

    @Bean(JOB_NAME)
    public Job rankingCorrectionJob(
        @Qualifier(STEP_NAME) Step rankingCorrectionStep
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(rankingCorrectionStep)
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_NAME)
    public Step rankingCorrectionStep(
        JdbcCursorItemReader<ProductMetricsRow> metricsReader,
        ItemWriter<ProductMetricsRow> redisRankingWriter
    ) {
        return new StepBuilder(STEP_NAME, jobRepository)
            .<ProductMetricsRow, ProductMetricsRow>chunk(CHUNK_SIZE, transactionManager)
            .reader(metricsReader)
            .writer(redisRankingWriter)
            .listener(stepMonitorListener)
            .build();
    }

    @StepScope
    @Bean
    public JdbcCursorItemReader<ProductMetricsRow> metricsReader() {
        return new JdbcCursorItemReaderBuilder<ProductMetricsRow>()
            .name("metricsReader")
            .dataSource(dataSource)
            .sql("SELECT pm.product_id, pm.view_count, " +
                "(pm.like_count - pm.unlike_count) AS net_like, " +
                "pm.sales_count, " +
                "(pm.sales_amount - pm.cancel_amount_by_event_date) AS net_sales_amount, " +
                "p.category_id " +
                "FROM product_metrics pm " +
                "JOIN product p ON pm.product_id = p.id " +
                "WHERE pm.metric_date = CURDATE() AND p.deleted_at IS NULL")
            .rowMapper((rs, rowNum) -> new ProductMetricsRow(
                rs.getLong("product_id"),
                rs.getLong("view_count"),
                rs.getLong("net_like"),
                rs.getLong("sales_count"),
                rs.getLong("net_sales_amount"),
                rs.getObject("category_id") != null ? rs.getLong("category_id") : null
            ))
            .build();
    }

    @SuppressWarnings("unchecked")
    @Bean
    public ItemWriter<ProductMetricsRow> redisRankingWriter(
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> writeTemplate
    ) {
        return chunk -> {
            LocalDate today = LocalDate.now(KST);
            String dateStr = today.format(DATE_FORMATTER);
            String zsetKey = RANKING_ZSET_PREFIX + dateStr;
            long nowEpochSeconds = Instant.now().getEpochSecond();

            writeTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    for (ProductMetricsRow row : chunk) {
                        String hashKey = RANKING_METRICS_PREFIX + dateStr + ":" + row.productId;

                        int categoryPriority = resolveCategoryPriority(row.categoryId);
                        double score = calculateScore(row, categoryPriority, nowEpochSeconds);

                        operations.delete(hashKey);
                        operations.opsForHash().putAll(hashKey, Map.of(
                            "viewCount", String.valueOf(row.viewCount),
                            "likeCount", String.valueOf(row.netLike),
                            "salesCount", String.valueOf(row.salesCount),
                            "salesAmount", String.valueOf(row.netSalesAmount),
                            "lastEventAt", String.valueOf(nowEpochSeconds)
                        ));
                        operations.opsForZSet().add(zsetKey, String.valueOf(row.productId), score);
                        operations.expire(hashKey, RANKING_HASH_TTL_SECONDS, TimeUnit.SECONDS);
                    }
                    operations.expire(zsetKey, RANKING_ZSET_TTL_SECONDS, TimeUnit.SECONDS);
                    return null;
                }
            });

            log.info("[RankingCorrection] chunk 처리 완료: products={}", chunk.size());
        };
    }

    double calculateScore(ProductMetricsRow row) {
        int categoryPriority = resolveCategoryPriority(row.categoryId);
        return calculateScore(row, categoryPriority, Instant.now().getEpochSecond());
    }

    double calculateScore(ProductMetricsRow row, int categoryPriority, long lastEventEpochSeconds) {
        return ScoreFormula.calculate(row.viewCount, row.netLike, row.netSalesAmount,
            categoryPriority, lastEventEpochSeconds, properties.weights());
    }

    private int resolveCategoryPriority(Long categoryId) {
        if (categoryId == null) return properties.defaultCategoryPriority();
        return properties.categoryPriority()
            .getOrDefault(categoryId, properties.defaultCategoryPriority());
    }

    record ProductMetricsRow(long productId, long viewCount, long netLike,
                             long salesCount, long netSalesAmount, Long categoryId) {}
}
