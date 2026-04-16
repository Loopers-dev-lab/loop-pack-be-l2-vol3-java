package com.loopers.batch.job.weekly;

import com.loopers.batch.job.weekly.step.WeeklyRankingItemWriter;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.ProductMetricsAggregate;
import com.loopers.config.RankingWeightsConfig.RankingWeights;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 주간 랭킹 집계 배치 Job 설정.
 *
 * 실행 조건:
 *   spring.batch.job.name=weeklyRankingJob 일 때만 Bean 이 활성화된다.
 *   (@ConditionalOnProperty 로 Job 별 컨텍스트 격리)
 *
 * 필수 JobParameter:
 *   targetDate (LocalDate) — 집계 기준 상한일. 슬라이딩 윈도우는 [targetDate-7, targetDate) 이다.
 *
 * 처리 흐름:
 *   1. Reader: product_metrics_hourly 를 7일 윈도우로 집계하여 score DESC 100건 커서 조회
 *   2. Writer: Chunk 순서(=rank) + targetDate-1 의 base_date 로 mv_product_rank_weekly 에 저장
 *
 * base_date 결정:
 *   batch 실행일이 targetDate 이고, MV 에는 targetDate-1(어제)을 base_date 로 적재한다.
 *   API 는 기본적으로 어제 날짜를 기준으로 랭킹을 조회하므로 일관성이 유지된다.
 */
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final String STEP_NAME = "weeklyRankingStep";

    /**
     * 집계 상위 N 건. SQL LIMIT 과 chunk size 를 동일 상수로 묶어
     * 둘 중 하나만 변경되는 실수를 방지한다.
     *
     * LIMIT = TOP_N, chunk size = TOP_N 이 일치해야 결과 전체가 단일 Chunk 로 처리되고
     * WeeklyRankingItemWriter 의 rank 할당(1부터 순서대로)이 올바르게 동작한다.
     */
    public static final int TOP_N = 100;

    /**
     * 슬라이딩 윈도우 7일 집계 SQL.
     *
     * 일간 랭킹(RankingScoreCalculator)과 동일한 공식을 사용한다.
     *   score = LN(1 + SUM(view_count))    * weightView
     *         + LN(1 + SUM(like_count))    * weightLike
     *         + LN(1 + SUM(order_amount))  * weightOrder
     *
     * order_count 가 아닌 order_amount 를 사용하는 이유 (week9.md A-3 참고):
     *   건수는 매출 기여를 무시하고, 금액 원본은 스케일 폭주로 view/like 를 압도한다.
     *   LN(1 + amount) 로 스케일을 압축하면 매출을 반영하면서 가중치 의미도 유지된다.
     *
     * 파라미터 순서:
     *   1: weightView  (view_count 가중치)
     *   2: weightLike  (like_count 가중치)
     *   3: weightOrder (order_amount 가중치)
     *   4: startTime   (targetDate - 7일 00:00, 포함)
     *   5: endTime     (targetDate 00:00, 미포함)
     */
    private static final String AGGREGATION_SQL = """
            SELECT
                product_id,
                LN(1 + SUM(view_count))   * ?
                + LN(1 + SUM(like_count)) * ?
                + LN(1 + SUM(order_amount)) * ? AS score
            FROM product_metrics_hourly
            WHERE bucket_hour >= ? AND bucket_hour < ?
            GROUP BY product_id
            ORDER BY score DESC
            LIMIT %d
            """.formatted(TOP_N);

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final PlatformTransactionManager transactionManager;
    private final WeeklyRankingItemWriter weeklyRankingItemWriter;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(weeklyRankingStep())
                .listener(jobListener)
                .build();
    }

    /**
     * 주간 랭킹 Step.
     *
     * chunk size = TOP_N = LIMIT 이므로 쿼리 결과 전체가 단일 write() 호출로 처리된다.
     * Writer 는 Chunk 순서를 기반으로 rank 를 1 부터 할당한다.
     *
     * @JobScope 를 적용하여 Job 실행마다 독립적인 Step 인스턴스를 생성한다.
     */
    @JobScope
    @Bean(STEP_NAME)
    public Step weeklyRankingStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<ProductMetricsAggregate, ProductMetricsAggregate>chunk(TOP_N, transactionManager)
                .reader(weeklyRankingReader(null, null, null))
                .writer(weeklyRankingItemWriter)
                .listener(stepMonitorListener)
                .build();
    }

    /**
     * 주간 집계 JdbcCursorItemReader.
     *
     * @StepScope 로 Job 실행 시점에 targetDate 를 바인딩한다.
     * null 로 선언된 파라미터는 @StepScope 프록시가 실제 Bean 을 주입하며,
     * 컴파일 경고를 방지하기 위해 null 을 명시한다.
     *
     * JdbcCursorItemReader 는 서버 커서를 사용하여 한 Row 씩 스트리밍하므로
     * 대량의 집계 결과도 메모리 부하 없이 처리할 수 있다.
     *
     * @param dataSource     @StepScope 에서 주입
     * @param rankingWeights @StepScope 에서 주입
     * @param targetDate     JobParameter 에서 바인딩
     */
    @StepScope
    @Bean("weeklyRankingReader")
    public JdbcCursorItemReader<ProductMetricsAggregate> weeklyRankingReader(
            DataSource dataSource,
            RankingWeights rankingWeights,
            @Value("#{jobParameters['targetDate']}") LocalDate targetDate
    ) {
        // 슬라이딩 윈도우: targetDate 기준 직전 7일
        LocalDateTime startTime = targetDate.minusDays(7).atStartOfDay();
        LocalDateTime endTime = targetDate.atStartOfDay();

        return new JdbcCursorItemReaderBuilder<ProductMetricsAggregate>()
                .name("weeklyRankingReader")
                .dataSource(dataSource)
                .sql(AGGREGATION_SQL)
                .preparedStatementSetter(ps -> {
                    ps.setDouble(1, rankingWeights.view());
                    ps.setDouble(2, rankingWeights.like());
                    ps.setDouble(3, rankingWeights.order());
                    ps.setObject(4, startTime);
                    ps.setObject(5, endTime);
                })
                .rowMapper((rs, rowNum) -> new ProductMetricsAggregate(
                        rs.getLong("product_id"),
                        rs.getDouble("score")
                ))
                .build();
    }
}
