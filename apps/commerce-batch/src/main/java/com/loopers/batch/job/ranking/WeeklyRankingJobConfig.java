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
import java.util.Calendar;
import java.util.TimeZone;

/**
 * 주간 랭킹 배치 Job 설정.
 *
 * 실행: --job.name=weeklyRankingJob --targetDate=yyyyMMdd
 *
 * 구조:
 *   Reader (JdbcCursorItemReader)
 *     ↓ ranking_event WHERE event_time ∈ [주 시작, 주 종료]
 *   Processor (score delta 계산, 0 delta는 필터)
 *     ↓
 *   Writer (WeeklyRankingAggregationWriter — StepExecutionListener 겸직)
 *     - write(): Map<productId, score> 누적
 *     - afterStep(): TOP 100 계산 + 단일 트랜잭션 DELETE + INSERT
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@EnableConfigurationProperties(RankingBatchProperties.class)
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final String STEP_NAME = "weeklyRankingStep";
    private static final String READER_NAME = "weeklyRankingEventReader";
    private static final int CHUNK_SIZE = 1000;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob(
            JobRepository jobRepository,
            JobListener jobListener,
            @Qualifier(STEP_NAME) Step weeklyRankingStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(weeklyRankingStep)
                .listener(jobListener)
                .build();
    }

    @Bean(STEP_NAME)
    @JobScope
    public Step weeklyRankingStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            StepMonitorListener stepMonitorListener,
            ChunkListener chunkListener,
            @Qualifier(READER_NAME) JdbcCursorItemReader<RankingEventRow> reader,
            @Qualifier("weeklyRankingProcessor") ItemProcessor<RankingEventRow, ScoredRankingEvent> processor,
            WeeklyRankingAggregationWriter writer) {
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
    public JdbcCursorItemReader<RankingEventRow> weeklyRankingEventReader(
            DataSource dataSource,
            @Value("#{jobParameters['targetDate']}") String targetDate) {

        LocalDate runDate = IsoPeriodMath.parseTargetDate(targetDate);
        LocalDate start = IsoPeriodMath.startOfIsoWeek(runDate);
        LocalDate end = IsoPeriodMath.endOfIsoWeek(runDate);
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
                    Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    ps.setTimestamp(1, fromTs, utcCal);
                    ps.setTimestamp(2, toTs, utcCal);
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

    @Bean("weeklyRankingProcessor")
    @StepScope
    public ItemProcessor<RankingEventRow, ScoredRankingEvent> weeklyRankingProcessor(
            RankingEventScorer scorer) {
        return row -> {
            double delta = scorer.calculateDelta(row.eventType());
            return delta == 0.0 ? null : new ScoredRankingEvent(row.productId(), delta);
        };
    }
}
