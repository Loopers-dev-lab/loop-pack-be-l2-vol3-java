package com.loopers.batch.job.ranking.measurement;

import com.loopers.batch.job.ranking.RollingRankingJobConfig;
import com.loopers.batch.job.ranking.fixture.BaselineSeeder;
import com.loopers.batch.job.ranking.fixture.SeedSpec;
import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.domain.ranking.weight.WeightConfig;
import com.loopers.domain.ranking.weight.WeightConfigRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선형성 / 스파이크 측정 벤치마크.
 *
 * <p>설계.md Phase 5 (#26~29) 의 "선형성 검증" + "스파이크 SLA" 측정.
 * 각 테스트는 시드 → Job 실행 → 결과를 stdout 으로 출력하여 shell script 가 파싱한다.</p>
 *
 * <p>평소 빌드에서는 {@code @Tag("benchmark")} 로 skip 되며, 명시적 명령으로만 실행:
 * {@code ./gradlew :apps:commerce-batch:test --tests "...Benchmark" -PrunBenchmark=true}</p>
 */
@Tag("benchmark")
@SpringBootTest
@SpringBatchTest
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@TestPropertySource(properties = "spring.batch.job.name=" + RollingRankingJobConfig.JOB_NAME)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RollingRankingJobBenchmark {

    private static final LocalDate ANCHOR = LocalDate.of(2026, 4, 14);
    private static final String ANCHOR_KEY = "20260414";

    @Autowired @Qualifier(RollingRankingJobConfig.JOB_NAME) private Job job;
    @Autowired private JobLauncher jobLauncher;
    @Autowired private WeightConfigRepository weightConfigRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        weightConfigRepository.save(new WeightConfig("control", 0.1, 0.2, 0.7, 100, true));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @Nested
    class 선형성_측정 {

        @Test
        void S단계_1000_product_실행_시간() throws Exception {
            runBenchmark("S", SeedSpec.small(ANCHOR));
        }

        @Test
        void M단계_5000_product_실행_시간() throws Exception {
            runBenchmark("M", SeedSpec.medium(ANCHOR));
        }

        @Test
        void L단계_20000_product_실행_시간() throws Exception {
            runBenchmark("L", SeedSpec.large(ANCHOR));
        }
    }

    @Nested
    class 스파이크_측정 {

        @Test
        void XL단계_스파이크_시뮬레이션_활동_product_5배() throws Exception {
            // L 의 5배 — Hot/Warm 의 일일 이벤트가 그만큼 폭증한 worst-case
            SeedSpec spike = new SeedSpec(100_000, ANCHOR, 30, 42L);
            runBenchmark("XL_SPIKE", spike);
        }
    }

    private void runBenchmark(String label, SeedSpec spec) throws Exception {
        // 1) 시드
        Instant seedStart = Instant.now();
        BaselineSeeder seeder = new BaselineSeeder(jdbcTemplate);
        BaselineSeeder.SeedReport seedReport = seeder.seed(spec);
        Duration seedDuration = Duration.between(seedStart, Instant.now());

        // 2) Job 실행
        Instant jobStart = Instant.now();
        JobParameters params = new JobParametersBuilder()
                .addString(RankingJobParametersListener.PARAM_ANCHOR_DATE, ANCHOR_KEY)
                .addLong("runTimestamp", System.nanoTime())
                .toJobParameters();
        JobExecution execution = jobLauncher.run(job, params);
        Duration jobDuration = Duration.between(jobStart, Instant.now());

        // 3) 결과를 파일에 append. (gradle test 가 stdout 을 캡처해 보이지 않으므로)
        long totalRows = (long) seedReport.viewRowsInserted()
                + seedReport.likeRowsInserted()
                + seedReport.orderRowsInserted();
        long mvCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_last_7d", Long.class);
        double tps = jobDuration.toMillis() == 0 ? 0
                : (double) totalRows / (jobDuration.toMillis() / 1000.0);

        String line = "BENCH| label=" + label
                + " status=" + execution.getStatus()
                + " totalProducts=" + spec.totalProducts()
                + " activeProducts=" + seedReport.activeProducts()
                + " seedRows=" + totalRows
                + " seedMs=" + seedDuration.toMillis()
                + " jobMs=" + jobDuration.toMillis()
                + " tpsRowsPerSec=" + String.format("%.1f", tps)
                + " mv7dCount=" + mvCount + "\n";

        // gradle test 의 working dir 는 :apps:commerce-batch 모듈 root 이므로 build/... 상대경로 사용
        java.nio.file.Path outPath = java.nio.file.Paths.get(
                System.getProperty("benchmark.outputFile", "build/benchmark-results.txt"));
        java.nio.file.Files.createDirectories(outPath.toAbsolutePath().getParent());
        java.nio.file.Files.writeString(outPath, line,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }
}
