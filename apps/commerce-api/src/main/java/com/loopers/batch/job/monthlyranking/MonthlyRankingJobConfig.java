package com.loopers.batch.job.monthlyranking;

import com.loopers.batch.job.common.RankingJobConstants;
import com.loopers.batch.job.common.RankingMetricsAggregation;
import com.loopers.infrastructure.persistence.jpa.rank.ProductRankMonthlyJpaEntity;
import com.loopers.infrastructure.persistence.jpa.rank.ProductRankMonthlyJpaRepository;
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
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 월간 랭킹 집계 Batch Job 설정.
 *
 * <p>Job 파라미터:
 * <ul>
 *   <li>targetDate (yyyyMMdd) - 이 날짜가 속한 월의 1일~말일 범위를 집계</li>
 * </ul>
 *
 * <p>처리 흐름:
 * <ol>
 *   <li>Reader: product_metrics_daily에서 해당 월간 데이터를 GROUP BY로 집계하여 읽기</li>
 *   <li>Processor: rank_number 부여 및 Entity 변환</li>
 *   <li>Writer: 기존 데이터 DELETE 후 INSERT (멱등성 보장)</li>
 * </ol>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MonthlyRankingJobConfig {

    private static final String JOB_NAME = "monthlyRankingJob";
    private static final String STEP_NAME = "monthlyRankingStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final ProductRankMonthlyJpaRepository monthlyRankRepository;

    @Bean
    public Job monthlyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(monthlyRankingStep())
            .build();
    }

    @Bean
    @JobScope
    public Step monthlyRankingStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
            .<RankingMetricsAggregation, ProductRankMonthlyJpaEntity>chunk(RankingJobConstants.CHUNK_SIZE, transactionManager)
            .reader(monthlyMetricsReader(null))
            .processor(monthlyRankingProcessor(null))
            .writer(monthlyRankingWriter(null))
            .build();
    }

    /**
     * Reader: 월간 메트릭 집계 데이터를 읽습니다.
     * GROUP BY product_id로 집계하고 score DESC로 정렬하여 TOP 100을 가져옵니다.
     */
    @Bean
    @StepScope
    public JdbcCursorItemReader<RankingMetricsAggregation> monthlyMetricsReader(
        @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate target = LocalDate.parse(targetDate, RankingJobConstants.DATE_FORMATTER);
        YearMonth yearMonth = YearMonth.from(target);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        log.info("Reading monthly metrics: targetDate={}, monthStart={}, monthEnd={}", targetDate, monthStart, monthEnd);

        String sql = RankingJobConstants.buildAggregationSql(monthStart.toString(), monthEnd.toString());

        return new JdbcCursorItemReaderBuilder<RankingMetricsAggregation>()
            .name("monthlyMetricsReader")
            .dataSource(dataSource)
            .sql(sql)
            .rowMapper(new RankingMetricsAggregation.RankingMetricsRowMapper())
            .build();
    }

    /**
     * Processor: 집계 데이터에 rank_number를 부여하고 Entity로 변환합니다.
     */
    @Bean
    @StepScope
    public ItemProcessor<RankingMetricsAggregation, ProductRankMonthlyJpaEntity> monthlyRankingProcessor(
        @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate target = LocalDate.parse(targetDate, RankingJobConstants.DATE_FORMATTER);
        YearMonth yearMonth = YearMonth.from(target);
        LocalDate monthStart = yearMonth.atDay(1);
        LocalDate monthEnd = yearMonth.atEndOfMonth();

        AtomicInteger rankCounter = new AtomicInteger(0);

        return aggregation -> {
            int rankNumber = rankCounter.incrementAndGet();
            return new ProductRankMonthlyJpaEntity(
                aggregation.productId(),
                rankNumber,
                aggregation.totalScore(),
                aggregation.totalViewCount(),
                aggregation.totalLikeCount(),
                aggregation.totalOrderCount(),
                monthStart,
                monthEnd
            );
        };
    }

    /**
     * Writer: 기존 월간 데이터를 삭제하고 새 데이터를 저장합니다.
     * DELETE + INSERT로 멱등성을 보장합니다.
     */
    @Bean
    @StepScope
    public ItemWriter<ProductRankMonthlyJpaEntity> monthlyRankingWriter(
        @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate target = LocalDate.parse(targetDate, RankingJobConstants.DATE_FORMATTER);
        YearMonth yearMonth = YearMonth.from(target);
        LocalDate monthStart = yearMonth.atDay(1);

        return items -> {
            if (!items.getItems().isEmpty()) {
                log.info("Deleting existing monthly ranking data: monthStart={}", monthStart);
                monthlyRankRepository.deleteByPeriodStartDate(monthStart);
            }

            log.info("Saving {} monthly ranking records for monthStart={}", items.size(), monthStart);
            monthlyRankRepository.saveAll(items.getItems());
        };
    }
}