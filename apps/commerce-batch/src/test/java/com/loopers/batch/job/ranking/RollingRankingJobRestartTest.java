package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.batch.job.ranking.step.stage.StagingViewMetricsWriter;
import com.loopers.domain.ranking.mv.MvProductRankLast30dRepository;
import com.loopers.domain.ranking.mv.MvProductRankLast7dRepository;
import com.loopers.domain.ranking.staging.StagingRankingAggregationRepository;
import com.loopers.domain.ranking.staging.StagingRankingScoredRepository;
import com.loopers.domain.ranking.weight.WeightConfig;
import com.loopers.domain.ranking.weight.WeightConfigRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;

/**
 * 설계.md 의 재시작 검증 시나리오 1/2/3.
 *
 * <p>모든 시나리오는 같은 anchorDate + 같은 runTimestamp 의 JobParameters 로
 * 두 번 launchJob 한다. Spring Batch 가 같은 JobInstance 의 직전 FAILED 를 감지해
 * 재시작 처리한다.</p>
 *
 * <p>실패 주입은 SpyBean 에 호출 카운트 기반 throw 를 doAnswer 로 설정.
 * 1차 실행이 FAILED 로 끝난 뒤 Mockito.reset 으로 throw 를 풀고 2차 실행한다.</p>
 */
@SpringBootTest
@SpringBatchTest
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@TestPropertySource(properties = "spring.batch.job.name=" + RollingRankingJobConfig.JOB_NAME)
class RollingRankingJobRestartTest {

    private static final String ANCHOR_KEY = "20260414";
    private static final LocalDate ANCHOR = LocalDate.of(2026, 4, 14);
    private static final LocalDateTime IN_7D = LocalDateTime.of(2026, 4, 10, 12, 0);

    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired @Qualifier(RollingRankingJobConfig.JOB_NAME) private Job job;
    @Autowired private JobLauncher jobLauncher;

    @Autowired private WeightConfigRepository weightConfigRepository;
    @Autowired private StagingRankingAggregationRepository stagingAggregationRepository;
    @Autowired private StagingRankingScoredRepository stagingScoredRepository;
    @Autowired private MvProductRankLast7dRepository last7dRepository;
    @Autowired private MvProductRankLast30dRepository last30dRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private RedisCleanUp redisCleanUp;

    @SpyBean private StagingViewMetricsWriter viewWriter;
    // WeightConfigRepository 는 ScoreProcessor (@BeforeStep) 와 PromoteTopToMvTasklet,
    // AuditTasklet, RedisRefreshTasklet 모두에서 호출됨.
    // 호출 순서 기반으로 throw 를 주입해 Step 5 또는 Step 5b 의 실패를 시뮬레이션한다.
    @SpyBean private WeightConfigRepository weightConfigRepoSpy;

    @AfterEach
    void tearDown() {
        Mockito.reset(viewWriter, weightConfigRepoSpy);
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    // ---------- Scenario 1 ----------

    @DisplayName("Scenario 1: Step 1 chunk 중 실패 → restart → 한 번에 돌렸을 때와 staging 결과 동일")
    @Test
    void scenario1_step1_chunkFailure_restart_yieldsSameAggregation() throws Exception {
        seedBaselineWeightConfig();
        // 여러 chunk 로 쪼개지도록 충분한 product 수 시드 (chunk size 500, 여기선 1200 product → 3 chunk)
        int totalProducts = 1200;
        for (long pid = 1; pid <= totalProducts; pid++) {
            saveView(pid, IN_7D, 10);
        }

        // 두 번째 chunk write 호출에서 throw → 1 chunk 만 commit 된 상태로 FAILED
        AtomicInteger calls = new AtomicInteger(0);
        Mockito.doAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) {
                throw new RuntimeException("의도적 chunk-mid 실패");
            }
            return invocation.callRealMethod();
        }).when(viewWriter).write(any());

        JobParameters params = paramsOf(ANCHOR_KEY, 1L);
        JobExecution first = jobLauncher.run(job, params);
        assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);

        // throw 해제 후 restart
        Mockito.reset(viewWriter);
        JobExecution second = jobLauncher.run(job, params);

        // 한 번에 돌렸을 때의 기대값 = 1200 product × view_count 10 → 각 product 합 10
        // staging_ranking_aggregation 에 (LAST_7D, LAST_30D) × 1200 = 2400 row 가 모두 view_count=10
        assertAll(
                () -> assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                () -> assertThat(stagingAggregationRepository.countByPeriodKey(ANCHOR_KEY))
                        .isEqualTo(totalProducts * 2L),
                // 첫 번째 product 의 LAST_7D row 가 정확히 10 (UPSERT 멱등성으로 중복 가산 안 됨)
                () -> assertThat(viewCount("LAST_7D", ANCHOR_KEY, 1L)).isEqualTo(10L)
        );
    }

    // ---------- Scenario 2a ----------

    @DisplayName("Scenario 2a: Step 5 (Score) 가 실패하면 MV 는 비어있다 (Step 5b 가 안 도므로)")
    @Test
    void scenario2a_step5Failure_keepsMvEmpty() throws Exception {
        seedBaselineWeightConfig();
        for (long pid = 1; pid <= 5; pid++) {
            saveView(pid, IN_7D, 10);
        }

        // WeightConfigRepository.findAllByActiveTrue 는 ScoreProcessor 의 @BeforeStep 에서
        // Step 5 시작 시 가장 먼저 호출됨. 첫 호출에서 throw → Step 5 fail.
        Mockito.doThrow(new RuntimeException("의도적 Step 5 실패"))
                .when(weightConfigRepoSpy).findAllByActiveTrue();

        JobParameters params = paramsOf(ANCHOR_KEY, 2L);
        JobExecution first = jobLauncher.run(job, params);

        assertAll(
                () -> assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED),
                // Step 5 가 실패해도 MV 는 비어있음 (Step 4a/4b 가 비운 그대로, Step 5b 안 돔)
                () -> assertThat(last7dRepository.countByAnchorDate(ANCHOR)).isZero(),
                () -> assertThat(last30dRepository.countByAnchorDate(ANCHOR)).isZero(),
                // 1차 staging 까지는 정상 적재됨 (Step 1~3 통과)
                () -> assertThat(stagingAggregationRepository.countByPeriodKey(ANCHOR_KEY)).isEqualTo(10L)
        );
    }

    // ---------- Scenario 2b ----------

    @DisplayName("Scenario 2b: Step 5b (Promote) 가 실패하면 2차 staging 은 적재된 채 MV 만 비어있다")
    @Test
    void scenario2b_step5bFailure_keepsScoredStaging_butMvEmpty() throws Exception {
        seedBaselineWeightConfig();
        for (long pid = 1; pid <= 5; pid++) {
            saveView(pid, IN_7D, 10);
        }

        // findAllByActiveTrue 호출 순서:
        //   #1 ScoreProcessor.@BeforeStep (Step 5)        → 통과
        //   #2 PromoteTopToMvTasklet.execute (Step 5b)     → throw
        AtomicInteger calls = new AtomicInteger(0);
        Mockito.doAnswer(invocation -> {
            if (calls.incrementAndGet() == 2) {
                throw new RuntimeException("의도적 Step 5b 실패");
            }
            return invocation.callRealMethod();
        }).when(weightConfigRepoSpy).findAllByActiveTrue();

        JobParameters params = paramsOf(ANCHOR_KEY, 3L);
        JobExecution first = jobLauncher.run(job, params);

        assertAll(
                () -> assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED),
                // Step 5 까지는 완주 → 2차 staging 에 (LAST_7D + LAST_30D) × 5 product = 10 row
                () -> assertThat(stagingScoredRepository.countByPeriodKey(ANCHOR_KEY)).isEqualTo(10L),
                // Step 5b 가 실패했으므로 MV 는 여전히 비어있음 (중간 상태 불가시성)
                () -> assertThat(last7dRepository.countByAnchorDate(ANCHOR)).isZero(),
                () -> assertThat(last30dRepository.countByAnchorDate(ANCHOR)).isZero()
        );
    }

    // ---------- Scenario 3 ----------

    @DisplayName("Scenario 3: 다른 anchorDate 는 서로 격리되어 한쪽이 다른쪽을 덮어쓰지 않는다")
    @Test
    void scenario3_differentAnchorsAreIsolated() throws Exception {
        seedBaselineWeightConfig();

        // anchor 20260414 용 데이터 (last7d 범위 안)
        saveView(1L, LocalDateTime.of(2026, 4, 10, 12, 0), 100);
        // anchor 20260413 용 데이터 (last7d 범위 안)
        saveView(2L, LocalDateTime.of(2026, 4, 9, 12, 0), 50);

        // 1차: anchorDate=20260414
        JobExecution exec1 = jobLauncher.run(job, paramsOf("20260414", 11L));
        // 2차: anchorDate=20260413 (백필 시나리오)
        JobExecution exec2 = jobLauncher.run(job, paramsOf("20260413", 12L));

        LocalDate anchor14 = LocalDate.of(2026, 4, 14);
        LocalDate anchor13 = LocalDate.of(2026, 4, 13);

        assertAll(
                () -> assertThat(exec1.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                () -> assertThat(exec2.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                // 두 anchor 의 MV 가 모두 보존됨
                () -> assertThat(last7dRepository.countByAnchorDate(anchor14)).isPositive(),
                () -> assertThat(last7dRepository.countByAnchorDate(anchor13)).isPositive(),
                // 두 anchor 의 staging 도 격리
                () -> assertThat(stagingAggregationRepository.countByPeriodKey("20260414")).isPositive(),
                () -> assertThat(stagingAggregationRepository.countByPeriodKey("20260413")).isPositive()
        );
    }

    // ---------- Scenario 4 ----------

    @DisplayName("Scenario 4: Hot product 가 bucket 1,000개를 소유해도 상품 중간 절단 없이 정확히 집계된다")
    @Test
    void scenario4_hotProductLongChain_noMidProductTruncation() throws Exception {
        seedBaselineWeightConfig();

        // product 1: bucket 1,000개 (chunk size=500 보다 큰 raw row 체인)
        // chunk 는 product 수 기준이므로 raw row 1,000개가 한 read() 호출에 전부 소비됨
        LocalDateTime baseTime = IN_7D;
        long expectedSum = 0;
        for (int i = 0; i < 1_000; i++) {
            long count = i + 1;
            saveView(1L, baseTime.plusMinutes(5L * i), count);
            expectedSum += count;
        }
        // product 2, 3: bucket 5개씩 (정상 크기)
        for (int i = 0; i < 5; i++) {
            saveView(2L, baseTime.plusMinutes(5L * i), 10);
            saveView(3L, baseTime.plusMinutes(5L * i), 10);
        }

        JobParameters params = paramsOf(ANCHOR_KEY, 4L);
        JobExecution execution = jobLauncher.run(job, params);

        long finalExpectedSum = expectedSum;
        assertAll(
                () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                // product 1 의 view_count 가 1+2+...+1000 = 1,000개 bucket 합계 (중간 절단 없음)
                () -> assertThat(viewCount("LAST_7D",  ANCHOR_KEY, 1L)).isEqualTo(finalExpectedSum),
                () -> assertThat(viewCount("LAST_30D", ANCHOR_KEY, 1L)).isEqualTo(finalExpectedSum),
                // product 2, 3 도 정상
                () -> assertThat(viewCount("LAST_7D", ANCHOR_KEY, 2L)).isEqualTo(50L),
                () -> assertThat(viewCount("LAST_7D", ANCHOR_KEY, 3L)).isEqualTo(50L),
                // 총 3 product × 2 period = 6 row
                () -> assertThat(stagingAggregationRepository.countByPeriodKey(ANCHOR_KEY)).isEqualTo(6L)
        );
    }

    // ---------- helpers ----------

    private void seedBaselineWeightConfig() {
        weightConfigRepository.save(new WeightConfig("control", 0.1, 0.2, 0.7, 100, true));
    }

    private void saveView(long productId, LocalDateTime bucketTime, long viewCount) {
        jdbcTemplate.update(
                "INSERT INTO product_view_metrics (product_id, bucket_time, view_count) VALUES (?, ?, ?)",
                productId, Timestamp.valueOf(bucketTime), viewCount);
    }

    private long viewCount(String periodType, String periodKey, long productId) {
        Long v = jdbcTemplate.queryForObject(
                "SELECT view_count FROM staging_ranking_aggregation " +
                        " WHERE period_type=? AND period_key=? AND product_id=?",
                Long.class, periodType, periodKey, productId);
        return v == null ? 0L : v;
    }

    /**
     * 같은 (anchorDate, runTimestamp) 페어는 같은 JobInstance 를 만들어
     * Spring Batch 가 직전 FAILED 를 자동 restart 처리한다.
     */
    private JobParameters paramsOf(String anchorDate, long runTimestamp) {
        return new JobParametersBuilder()
                .addString(RankingJobParametersListener.PARAM_ANCHOR_DATE, anchorDate)
                .addLong("runTimestamp", runTimestamp)
                .toJobParameters();
    }
}
