package com.loopers.batch.job.ranking;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.infrastructure.metrics.ProductMetricsAggregatedDto;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyEntity;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingMonthlyJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class RankingMonthlyJobConfig {

    public static final String JOB_NAME = "rankingMonthlyJob";
    private static final int CHUNK_SIZE = 100;
    private static final int TOP_RANK_LIMIT = 100;
    private static final DateTimeFormatter YEAR_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final MvProductRankMonthlyJpaRepository monthlyRepository;

    @Bean(JOB_NAME)
    public Job rankingMonthlyJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(monthlyScoreCalculationStep())
                .next(monthlyRankAssignStep())
                .listener(jobListener)
                .build();
    }

    @Bean("monthlyScoreCalculationStep")
    public Step monthlyScoreCalculationStep() {
        return new StepBuilder("monthlyScoreCalculationStep", jobRepository)
                .<ProductMetricsAggregatedDto, MvProductRankMonthlyEntity>chunk(CHUNK_SIZE, transactionManager)
                .reader(monthlyProductMetricsReader(null))
                .processor(monthlyRankingProcessor(null))
                .writer(monthlyMvWriter(null))
                .listener(stepMonitorListener)
                .build();
    }

    @Bean("monthlyRankAssignStep")
    public Step monthlyRankAssignStep() {
        return new StepBuilder("monthlyRankAssignStep", jobRepository)
                .tasklet(monthlyRankAssignTasklet(null), transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    @StepScope
    @Bean("monthlyProductMetricsReader")
    public JdbcPagingItemReader<ProductMetricsAggregatedDto> monthlyProductMetricsReader(
            @Value("#{jobParameters['targetDate']}") String targetDate) {
        LocalDate target = LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE);
        LocalDate startDate = target.withDayOfMonth(1);
        LocalDate endDate = target.withDayOfMonth(target.lengthOfMonth());

        log.info("월간 Reader 기간: {} ~ {}", startDate, endDate);

        return new JdbcPagingItemReaderBuilder<ProductMetricsAggregatedDto>()
                .name("monthlyProductMetricsReader")
                .dataSource(dataSource)
                .selectClause("product_id, SUM(view_count) AS total_view_count, SUM(like_count) AS total_like_count, SUM(total_quantity) AS total_quantity")
                .fromClause("product_metrics")
                .whereClause("metrics_date BETWEEN :startDate AND :endDate")
                .groupClause("product_id")
                .sortKeys(Map.of("product_id", Order.ASCENDING))
                .parameterValues(Map.of("startDate", Date.valueOf(startDate), "endDate", Date.valueOf(endDate)))
                .rowMapper((rs, rowNum) -> new ProductMetricsAggregatedDto(
                        rs.getLong("product_id"),
                        rs.getLong("total_view_count"),
                        rs.getLong("total_like_count"),
                        rs.getLong("total_quantity")
                ))
                .pageSize(CHUNK_SIZE)
                .build();
    }

    @StepScope
    @Bean("monthlyRankingProcessor")
    public ItemProcessor<ProductMetricsAggregatedDto, MvProductRankMonthlyEntity> monthlyRankingProcessor(
            @Value("#{jobParameters['targetDate']}") String targetDate) {
        LocalDate target = LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE);
        String yearMonth = target.format(YEAR_MONTH_FORMAT);

        return dto -> {
            double score = 0.1 * dto.getTotalViewCount()
                    + 0.2 * dto.getTotalLikeCount()
                    + 0.7 * Math.log1p(dto.getTotalQuantity());
            return new MvProductRankMonthlyEntity(dto.getProductId(), score, yearMonth, 0);
        };
    }

    @StepScope
    @Bean("monthlyMvWriter")
    public ItemWriter<MvProductRankMonthlyEntity> monthlyMvWriter(
            @Value("#{jobParameters['targetDate']}") String targetDate) {
        LocalDate target = LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE);
        String yearMonth = target.format(YEAR_MONTH_FORMAT);

        return chunk -> {
            for (MvProductRankMonthlyEntity item : chunk.getItems()) {
                monthlyRepository.findById(item.getProductId())
                        .ifPresentOrElse(
                                existing -> existing.update(item.getScore(), yearMonth, 0),
                                () -> monthlyRepository.save(item)
                        );
            }
        };
    }

    @StepScope
    @Bean("monthlyRankAssignTasklet")
    public Tasklet monthlyRankAssignTasklet(
            @Value("#{jobParameters['targetDate']}") String targetDate) {
        LocalDate target = LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE);
        String yearMonth = target.format(YEAR_MONTH_FORMAT);

        return (contribution, chunkContext) -> {
            monthlyRepository.deleteAllByYearMonthNot(yearMonth);

            List<MvProductRankMonthlyEntity> ranked = monthlyRepository.findAllByYearMonthOrderByScoreDesc(yearMonth);

            for (int i = 0; i < Math.min(ranked.size(), TOP_RANK_LIMIT); i++) {
                ranked.get(i).update(ranked.get(i).getScore(), yearMonth, i + 1);
            }

            if (ranked.size() > TOP_RANK_LIMIT) {
                monthlyRepository.deleteAll(ranked.subList(TOP_RANK_LIMIT, ranked.size()));
                log.info("월간 랭킹 {}위 이후 {}건 삭제", TOP_RANK_LIMIT, ranked.size() - TOP_RANK_LIMIT);
            }

            log.info("월간 랭킹 확정: {}건 (yearMonth={})", Math.min(ranked.size(), TOP_RANK_LIMIT), yearMonth);
            return RepeatStatus.FINISHED;
        };
    }
}
