package com.loopers.batch.job.weeklyranking;

import com.loopers.batch.job.common.RankingJobConstants;
import com.loopers.batch.job.common.RankingMetricsAggregation;
import com.loopers.infrastructure.persistence.jpa.rank.ProductRankWeeklyJpaEntity;
import com.loopers.infrastructure.persistence.jpa.rank.ProductRankWeeklyJpaRepository;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 주간 랭킹 집계 Batch Job 설정.
 *
 * <p>Job 파라미터:
 * <ul>
 *   <li>targetDate (yyyyMMdd) - 이 날짜가 속한 주의 월~일 범위를 집계</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WeeklyRankingJobConfig {

    private static final String JOB_NAME = "weeklyRankingJob";
    private static final String STEP_NAME = "weeklyRankingStep";

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final ProductRankWeeklyJpaRepository weeklyRankRepository;

    @Bean
    public Job weeklyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(weeklyRankingStep())
            .build();
    }

    @Bean
    @JobScope
    public Step weeklyRankingStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
            .<RankingMetricsAggregation, ProductRankWeeklyJpaEntity>chunk(RankingJobConstants.CHUNK_SIZE, transactionManager)
            .reader(weeklyMetricsReader(null))
            .processor(weeklyRankingProcessor(null))
            .writer(weeklyRankingWriter(null))
            .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<RankingMetricsAggregation> weeklyMetricsReader(
        @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate target = LocalDate.parse(targetDate, RankingJobConstants.DATE_FORMATTER);
        LocalDate weekStart = target.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = target.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        log.info("Reading weekly metrics: targetDate={}, weekStart={}, weekEnd={}", targetDate, weekStart, weekEnd);

        String sql = RankingJobConstants.buildAggregationSql(weekStart.toString(), weekEnd.toString());

        return new JdbcCursorItemReaderBuilder<RankingMetricsAggregation>()
            .name("weeklyMetricsReader")
            .dataSource(dataSource)
            .sql(sql)
            .rowMapper(new RankingMetricsAggregation.RankingMetricsRowMapper())
            .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<RankingMetricsAggregation, ProductRankWeeklyJpaEntity> weeklyRankingProcessor(
        @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate target = LocalDate.parse(targetDate, RankingJobConstants.DATE_FORMATTER);
        LocalDate weekStart = target.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = target.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        AtomicInteger rankCounter = new AtomicInteger(0);

        return aggregation -> {
            int rankNumber = rankCounter.incrementAndGet();
            return new ProductRankWeeklyJpaEntity(
                aggregation.productId(),
                rankNumber,
                aggregation.totalScore(),
                aggregation.totalViewCount(),
                aggregation.totalLikeCount(),
                aggregation.totalOrderCount(),
                weekStart,
                weekEnd
            );
        };
    }

    @Bean
    @StepScope
    public ItemWriter<ProductRankWeeklyJpaEntity> weeklyRankingWriter(
        @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate target = LocalDate.parse(targetDate, RankingJobConstants.DATE_FORMATTER);
        LocalDate weekStart = target.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        return items -> {
            if (!items.getItems().isEmpty()) {
                log.info("Deleting existing weekly ranking data: weekStart={}", weekStart);
                weeklyRankRepository.deleteByPeriodStartDate(weekStart);
            }

            log.info("Saving {} weekly ranking records for weekStart={}", items.size(), weekStart);
            weeklyRankRepository.saveAll(items.getItems());
        };
    }
}