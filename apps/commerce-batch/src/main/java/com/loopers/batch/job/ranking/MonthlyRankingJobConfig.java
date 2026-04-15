package com.loopers.batch.job.ranking;

import com.loopers.batch.config.RankingBatchProperties;
import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * 월간 랭킹 배치 Job 설정.
 *
 * 실행: --job.name=monthlyRankingJob --targetDate=yyyyMMdd
 *
 * 주간과 동일 구조. 경계만 월 단위 (targetDate가 포함된 달 1일~말일).
 * 주→월 합산 금지 — 월은 항상 ranking_event에서 독립 재집계.
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@EnableConfigurationProperties(RankingBatchProperties.class)
@Configuration
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";
    private static final String STEP_NAME = "monthlyRankingStep";
    private static final String READER_NAME = "monthlyRankingEventReader";
    private static final int CHUNK_SIZE = 1000;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob(
            JobRepository jobRepository,
            JobListener jobListener,
            @Qualifier(STEP_NAME) Step monthlyRankingStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(monthlyRankingStep)
                .listener(jobListener)
                .build();
    }

    @Bean(STEP_NAME)
    @JobScope
    public Step monthlyRankingStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            StepMonitorListener stepMonitorListener,
            ChunkListener chunkListener,
            @Qualifier(READER_NAME) JdbcCursorItemReader<RankingEventRow> reader,
            @Qualifier("monthlyRankingProcessor") ItemProcessor<RankingEventRow, ScoredRankingEvent> processor,
            MonthlyRankingAggregationWriter writer) {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<RankingEventRow, ScoredRankingEvent>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener((StepExecutionListener) writer)
                .listener(stepMonitorListener)
                .listener(chunkListener)
                .build();
    }

    @Bean(READER_NAME)
    @StepScope
    public JdbcCursorItemReader<RankingEventRow> monthlyRankingEventReader(
            DataSource dataSource,
            @Value("#{jobParameters['targetDate']}") String targetDate) {

        LocalDate runDate = IsoPeriodMath.parseTargetDate(targetDate);
        LocalDate start = IsoPeriodMath.startOfMonth(runDate);
        LocalDate end = IsoPeriodMath.endOfMonth(runDate);
        Timestamp fromTs = Timestamp.from(IsoPeriodMath.startOfDayKst(start).toInstant());
        Timestamp toTs = Timestamp.from(IsoPeriodMath.endOfDayKst(end).toInstant());

        return new JdbcCursorItemReaderBuilder<RankingEventRow>()
                .name(READER_NAME)
                .dataSource(dataSource)
                .sql("""
                        SELECT id, product_id, event_type, event_time
                        FROM ranking_event
                        WHERE event_time BETWEEN ? AND ?
                        ORDER BY id ASC
                        """)
                .preparedStatementSetter(ps -> {
                    ps.setTimestamp(1, fromTs);
                    ps.setTimestamp(2, toTs);
                })
                .rowMapper((rs, rowNum) -> new RankingEventRow(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        rs.getString("event_type"),
                        rs.getTimestamp("event_time").toInstant().atZone(IsoPeriodMath.KST)
                ))
                .fetchSize(1000)
                .build();
    }

    @Bean("monthlyRankingProcessor")
    @StepScope
    public ItemProcessor<RankingEventRow, ScoredRankingEvent> monthlyRankingProcessor(
            RankingEventScorer scorer) {
        return row -> {
            double delta = scorer.calculateDelta(row.eventType());
            return delta == 0.0 ? null : new ScoredRankingEvent(row.productId(), delta);
        };
    }
}
