package com.loopers.batch.job.ranking;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.infrastructure.metrics.ProductMetricsAggregatedDto;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyEntity;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
import java.sql.Date;
import java.time.DayOfWeek;
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
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingWeeklyJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class RankingWeeklyJobConfig {

    public static final String JOB_NAME = "rankingWeeklyJob";
    private static final int CHUNK_SIZE = 100;
    private static final int TOP_RANK_LIMIT = 100;

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final MvProductRankWeeklyJpaRepository weeklyRepository;

    @Bean(JOB_NAME)
    public Job rankingWeeklyJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(weeklyScoreCalculationStep())
                .next(weeklyRankAssignStep())
                .listener(jobListener)
                .build();
    }

    @Bean("weeklyScoreCalculationStep")
    public Step weeklyScoreCalculationStep() {
        return new StepBuilder("weeklyScoreCalculationStep", jobRepository)
                .<ProductMetricsAggregatedDto, MvProductRankWeeklyEntity>chunk(CHUNK_SIZE, transactionManager)
                .reader(weeklyProductMetricsReader(null))
                .processor(weeklyRankingProcessor(null))
                .writer(weeklyMvWriter(null))
                .listener(stepMonitorListener)
                .build();
    }

    @Bean("weeklyRankAssignStep")
    public Step weeklyRankAssignStep() {
        return new StepBuilder("weeklyRankAssignStep", jobRepository)
                .tasklet(weeklyRankAssignTasklet(null), transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    @StepScope
    @Bean("weeklyProductMetricsReader")
    public JdbcPagingItemReader<ProductMetricsAggregatedDto> weeklyProductMetricsReader(
            @Value("#{jobParameters['targetDate']}") String targetDate) {
        LocalDate target = LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE);
        LocalDate startDate = target.with(DayOfWeek.MONDAY);
        LocalDate endDate = target.with(DayOfWeek.SUNDAY);

        log.info("주간 Reader 기간: {} ~ {}", startDate, endDate);

        return new JdbcPagingItemReaderBuilder<ProductMetricsAggregatedDto>()
                .name("weeklyProductMetricsReader")
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
    @Bean("weeklyRankingProcessor")
    public ItemProcessor<ProductMetricsAggregatedDto, MvProductRankWeeklyEntity> weeklyRankingProcessor(
            @Value("#{jobParameters['targetDate']}") String targetDate) {
        LocalDate target = LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE);
        String yearWeek = target.with(DayOfWeek.MONDAY).format(DateTimeFormatter.BASIC_ISO_DATE);

        return dto -> {
            double score = 0.1 * dto.getTotalViewCount()
                    + 0.2 * dto.getTotalLikeCount()
                    + 0.7 * Math.log1p(dto.getTotalQuantity());
            return new MvProductRankWeeklyEntity(dto.getProductId(), score, yearWeek, 0);
        };
    }

    @StepScope
    @Bean("weeklyMvWriter")
    public ItemWriter<MvProductRankWeeklyEntity> weeklyMvWriter(
            @Value("#{jobParameters['targetDate']}") String targetDate) {
        LocalDate target = LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE);
        String yearWeek = target.with(DayOfWeek.MONDAY).format(DateTimeFormatter.BASIC_ISO_DATE);

        return chunk -> {
            for (MvProductRankWeeklyEntity item : chunk.getItems()) {
                weeklyRepository.findById(item.getProductId())
                        .ifPresentOrElse(
                                existing -> existing.update(item.getScore(), yearWeek, 0),
                                () -> weeklyRepository.save(item)
                        );
            }
        };
    }

    @StepScope
    @Bean("weeklyRankAssignTasklet")
    public Tasklet weeklyRankAssignTasklet(
            @Value("#{jobParameters['targetDate']}") String targetDate) {
        LocalDate target = LocalDate.parse(targetDate, DateTimeFormatter.BASIC_ISO_DATE);
        String yearWeek = target.with(DayOfWeek.MONDAY).format(DateTimeFormatter.BASIC_ISO_DATE);

        return (contribution, chunkContext) -> {
            weeklyRepository.deleteAllByYearWeekNot(yearWeek);

            List<MvProductRankWeeklyEntity> ranked = weeklyRepository.findAllByYearWeekOrderByScoreDesc(yearWeek);

            for (int i = 0; i < Math.min(ranked.size(), TOP_RANK_LIMIT); i++) {
                ranked.get(i).update(ranked.get(i).getScore(), yearWeek, i + 1);
            }

            if (ranked.size() > TOP_RANK_LIMIT) {
                weeklyRepository.deleteAll(ranked.subList(TOP_RANK_LIMIT, ranked.size()));
                log.info("주간 랭킹 {}위 이후 {}건 삭제", TOP_RANK_LIMIT, ranked.size() - TOP_RANK_LIMIT);
            }

            log.info("주간 랭킹 확정: {}건 (yearWeek={})", Math.min(ranked.size(), TOP_RANK_LIMIT), yearWeek);
            return RepeatStatus.FINISHED;
        };
    }
}
