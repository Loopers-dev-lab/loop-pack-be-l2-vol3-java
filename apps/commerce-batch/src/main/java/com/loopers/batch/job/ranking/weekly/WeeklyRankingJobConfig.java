package com.loopers.batch.job.ranking.weekly;

import com.loopers.batch.job.ranking.ProductMetricsRow;
import com.loopers.batch.job.ranking.weekly.step.ClearWeeklyRankingTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.MvProductRankWeeklyModel;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 주간 랭킹 집계 배치 Job 설정.
 *
 * Job 구조:
 *   Step 1 (Tasklet) — 해당 주차 기존 MV 데이터 삭제
 *   Step 2 (Chunk)   — product_metrics 읽기 → 점수 계산/순위 부여 → MV 적재
 *
 * 실행:
 *   ./gradlew :apps:commerce-batch:bootRun \
 *     --args="--spring.batch.job.name=weeklyRankingJob --job.name=weeklyRankingJob --requestDate=20260412"
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final String STEP_CLEAR = "clearWeeklyRankingStep";
    private static final String STEP_AGGREGATE = "aggregateWeeklyRankingStep";
    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ClearWeeklyRankingTasklet clearWeeklyRankingTasklet;

    // ============================================================
    // Job 정의
    // ============================================================

    @Bean(JOB_NAME)
    public Job weeklyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(clearWeeklyRankingStep())     // Step 1: 기존 데이터 삭제
                .next(aggregateWeeklyRankingStep())   // Step 2: 집계 + 적재
                .listener(jobListener)
                .build();
    }

    // ============================================================
    // Step 1: 기존 MV 데이터 삭제 (Tasklet)
    // ============================================================

    @JobScope
    @Bean(STEP_CLEAR)
    public Step clearWeeklyRankingStep() {
        return new StepBuilder(STEP_CLEAR, jobRepository)
                .tasklet(clearWeeklyRankingTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    // ============================================================
    // Step 2: 집계 + 적재 (Chunk-Oriented)
    // ============================================================

    @JobScope
    @Bean(STEP_AGGREGATE)
    public Step aggregateWeeklyRankingStep() {
        return new StepBuilder(STEP_AGGREGATE, jobRepository)
                .<ProductMetricsRow, MvProductRankWeeklyModel>chunk(CHUNK_SIZE, transactionManager)
                .reader(weeklyRankingReader(null))          // DataSource는 Spring이 주입
                .processor(weeklyRankingProcessor(null))     // requestDate는 @StepScope에서 주입
                .writer(weeklyRankingWriter(null))           // EntityManagerFactory는 Spring이 주입
                .listener(stepMonitorListener)
                .build();
    }

    // ---- Reader: product_metrics에서 가중치 점수 기준 TOP 100 조회 ----

    /**
     * JdbcCursorItemReader: DB 커서를 열고 1행씩 읽는다.
     * commerce-batch에는 ProductMetricsModel 엔티티가 없으므로 JDBC로 직접 읽는다.
     *
     * SQL 설명:
     *   - product_metrics의 모든 행에 가중치 점수를 계산
     *   - 점수 내림차순으로 정렬하여 상위 100건만 조회
     *   - 가중치: view * 0.1 + like * 0.2 + salesCount * 0.7
     */
    @StepScope
    @Bean
    public JdbcCursorItemReader<ProductMetricsRow> weeklyRankingReader(DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<ProductMetricsRow>()
                .name("weeklyRankingReader")
                .dataSource(dataSource)
                .sql("""
                    SELECT pm.product_id,
                           pm.view_count,
                           pm.like_count,
                           pm.sales_count,
                           (pm.view_count * 0.1 + pm.like_count * 0.2 + pm.sales_count * 0.7) AS score
                    FROM product_metrics pm
                    ORDER BY score DESC
                    LIMIT 100
                    """)
                .rowMapper((rs, rowNum) -> new ProductMetricsRow(
                        rs.getLong("product_id"),
                        rs.getLong("view_count"),
                        rs.getLong("like_count"),
                        rs.getLong("sales_count"),
                        rs.getDouble("score")))
                .build();
    }

    // ---- Processor: 점수 계산 + yearWeek 계산 + 순위 부여 ----

    /**
     * ProductMetricsRow → MvProductRankWeeklyModel 변환.
     * - requestDate로부터 yearWeek을 계산한다
     * - rankCounter로 읽은 순서대로 순위를 부여한다 (Reader가 점수 DESC로 정렬했으므로)
     *
     * @StepScope: Step마다 새 인스턴스 생성 → rankCounter가 0으로 리셋됨
     */
    @StepScope
    @Bean
    public ItemProcessor<ProductMetricsRow, MvProductRankWeeklyModel> weeklyRankingProcessor(
            @Value("#{jobParameters['requestDate']}") String requestDate
    ) {
        AtomicInteger rankCounter = new AtomicInteger(0);
        String yearWeek = ClearWeeklyRankingTasklet.computeYearWeek(requestDate);

        return item -> new MvProductRankWeeklyModel(
                item.productId(),
                item.viewCount(),
                item.likeCount(),
                item.salesCount(),
                item.score(),
                rankCounter.incrementAndGet(),  // 1, 2, 3, ... 순위 부여
                yearWeek
        );
    }

    // ---- Writer: MV 테이블에 JPA로 저장 ----

    @StepScope
    @Bean
    public JpaItemWriter<MvProductRankWeeklyModel> weeklyRankingWriter(
            EntityManagerFactory entityManagerFactory
    ) {
        return new JpaItemWriterBuilder<MvProductRankWeeklyModel>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }
}
