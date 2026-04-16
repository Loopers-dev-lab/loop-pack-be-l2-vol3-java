package com.loopers.job.monthly;

import com.loopers.batch.job.monthly.MonthlyRankingJobConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + MonthlyRankingJobConfig.JOB_NAME)
class MonthlyRankingJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(MonthlyRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM mv_product_rank_monthly");
        jdbcTemplate.execute("DELETE FROM product_metrics_hourly");
    }

    @DisplayName("targetDate 파라미터 없이 실행하면 명시적 오류 메시지와 함께 배치가 실패한다.")
    @Test
    void failsWithoutTargetDate() throws Exception {
        // given
        jobLauncherTestUtils.setJob(job);

        // when
        var jobExecution = jobLauncherTestUtils.launchJob();

        // then
        assertAll(
                () -> assertThat(jobExecution.getExitStatus().getExitCode())
                        .isEqualTo(ExitStatus.FAILED.getExitCode()),
                () -> assertThat(jobExecution.getAllFailureExceptions())
                        .anyMatch(e -> e.getMessage() != null && e.getMessage().contains("targetDate"))
        );
    }

    @DisplayName("집계 대상 데이터가 없을 때 배치가 COMPLETED 되고 MV 테이블에 데이터가 없다.")
    @Test
    void completedWithEmptyMetrics() throws Exception {
        // given
        jobLauncherTestUtils.setJob(job);
        LocalDate targetDate = LocalDate.of(2026, 4, 10);

        // when
        var jobParameters = new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .toJobParameters();
        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertAll(
                () -> assertThat(jobExecution.getExitStatus().getExitCode())
                        .isEqualTo(ExitStatus.COMPLETED.getExitCode()),
                () -> assertThat(countByBaseDate("mv_product_rank_monthly", targetDate.minusDays(1))).isZero()
        );
    }

    @DisplayName("집계 대상 데이터가 있으면 MV 테이블에 score 내림차순으로 랭킹이 적재된다.")
    @Test
    void populatesMvTableWithRanking() throws Exception {
        // given — score 공식: LN(1+view)*0.1 + LN(1+like)*0.2 + LN(1+orderAmount)*0.7
        LocalDate targetDate = LocalDate.of(2026, 4, 11);
        LocalDateTime bucket = targetDate.minusDays(10).atTime(12, 0); // 30일 윈도우 내
        insertMetrics(1L, bucket, 0, 0, 0, 10000.0); // LN(10001)*0.7 ≈ 6.45 → rank 1
        insertMetrics(2L, bucket, 30, 0, 0, 0.0);    // LN(31)*0.1   ≈ 0.34 → rank 2
        jobLauncherTestUtils.setJob(job);

        // when
        var jobParameters = new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .toJobParameters();
        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        LocalDate baseDate = targetDate.minusDays(1);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT product_id, `rank`, score FROM mv_product_rank_monthly WHERE base_date = ? ORDER BY `rank`",
                baseDate
        );
        assertAll(
                () -> assertThat(jobExecution.getExitStatus().getExitCode())
                        .isEqualTo(ExitStatus.COMPLETED.getExitCode()),
                () -> assertThat(rows).hasSize(2),
                () -> assertThat(((Number) rows.get(0).get("product_id")).longValue()).isEqualTo(1L),
                () -> assertThat(((Number) rows.get(0).get("rank")).intValue()).isEqualTo(1),
                () -> assertThat(((Number) rows.get(1).get("product_id")).longValue()).isEqualTo(2L),
                () -> assertThat(((Number) rows.get(1).get("rank")).intValue()).isEqualTo(2)
        );
    }

    @DisplayName("재실행 시 기존 MV 데이터가 새 랭킹으로 교체된다.")
    @Test
    void replacesExistingMvOnRerun() throws Exception {
        // given — 1차 실행 완료: product 1만 적재된 상태
        LocalDate targetDate = LocalDate.of(2026, 4, 12);
        LocalDateTime bucket = targetDate.minusDays(10).atTime(12, 0);
        insertMetrics(1L, bucket, 0, 0, 0, 5000.0); // LN(5001)*0.7 ≈ 5.95
        jobLauncherTestUtils.setJob(job);
        jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .toJobParameters());

        // when — product 2 추가 후 동일 targetDate 로 재실행 (run.id 로 새 JobInstance 생성)
        insertMetrics(2L, bucket, 0, 0, 0, 10000.0); // LN(10001)*0.7 ≈ 6.45
        var jobExecution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .addLong("run.id", 2L)
                .toJobParameters());

        // then — product 2 가 order_amount 더 높아서 rank 1
        LocalDate baseDate = targetDate.minusDays(1);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT product_id, `rank` FROM mv_product_rank_monthly WHERE base_date = ? ORDER BY `rank`",
                baseDate
        );
        assertAll(
                () -> assertThat(jobExecution.getExitStatus().getExitCode())
                        .isEqualTo(ExitStatus.COMPLETED.getExitCode()),
                () -> assertThat(rows).hasSize(2),
                () -> assertThat(((Number) rows.get(0).get("product_id")).longValue()).isEqualTo(2L),
                () -> assertThat(((Number) rows.get(0).get("rank")).intValue()).isEqualTo(1)
        );
    }

    @DisplayName("슬라이딩 윈도우 바깥 데이터는 집계에서 제외된다.")
    @Test
    void excludesDataOutsideWindow() throws Exception {
        // given — 윈도우: [targetDate-30일 00:00, targetDate 00:00)
        LocalDate targetDate = LocalDate.of(2026, 4, 20);
        LocalDateTime outsideWindow = targetDate.minusDays(31).atTime(12, 0); // 윈도우 시작보다 하루 이전
        LocalDateTime insideWindow = targetDate.minusDays(29).atTime(12, 0);  // 윈도우 내
        insertMetrics(1L, outsideWindow, 0, 0, 0, 50000.0); // 집계 제외
        insertMetrics(2L, insideWindow, 0, 0, 0, 1000.0);   // 집계 포함
        jobLauncherTestUtils.setJob(job);

        // when
        var jobExecution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .toJobParameters());

        // then — product 2만 MV 에 적재되어야 한다
        LocalDate baseDate = targetDate.minusDays(1);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT product_id, `rank` FROM mv_product_rank_monthly WHERE base_date = ? ORDER BY `rank`",
                baseDate
        );
        assertAll(
                () -> assertThat(jobExecution.getExitStatus().getExitCode())
                        .isEqualTo(ExitStatus.COMPLETED.getExitCode()),
                () -> assertThat(rows).hasSize(1),
                () -> assertThat(((Number) rows.get(0).get("product_id")).longValue()).isEqualTo(2L)
        );
    }

    @DisplayName("동일 상품의 여러 bucket_hour 데이터가 합산되어 점수가 계산된다.")
    @Test
    void aggregatesMultipleBucketHoursForSameProduct() throws Exception {
        // given — product 1: 두 시간대 합산 order_amount=10000, product 2: 단일 order_amount=9000
        LocalDate targetDate = LocalDate.of(2026, 4, 21);
        LocalDateTime bucket1 = targetDate.minusDays(10).atTime(10, 0);
        LocalDateTime bucket2 = targetDate.minusDays(10).atTime(11, 0);
        insertMetrics(1L, bucket1, 0, 0, 0, 5000.0);
        insertMetrics(1L, bucket2, 0, 0, 0, 5000.0); // 합산 10000 → LN(10001)*0.7 ≈ 6.45
        insertMetrics(2L, bucket1, 0, 0, 0, 9000.0); // 단일 9000  → LN(9001)*0.7  ≈ 6.28
        jobLauncherTestUtils.setJob(job);

        // when
        var jobExecution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .toJobParameters());

        // then — product 1이 합산 score 가 높아 rank 1
        LocalDate baseDate = targetDate.minusDays(1);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT product_id, `rank` FROM mv_product_rank_monthly WHERE base_date = ? ORDER BY `rank`",
                baseDate
        );
        assertAll(
                () -> assertThat(jobExecution.getExitStatus().getExitCode())
                        .isEqualTo(ExitStatus.COMPLETED.getExitCode()),
                () -> assertThat(rows).hasSize(2),
                () -> assertThat(((Number) rows.get(0).get("product_id")).longValue()).isEqualTo(1L),
                () -> assertThat(((Number) rows.get(0).get("rank")).intValue()).isEqualTo(1)
        );
    }

    @DisplayName("기존 MV 데이터 존재 + 동일 targetDate 재실행 시 metrics 0건이면 MV도 0건이 된다.")
    @Test
    void clearsStaleDataWhenMetricsEmpty() throws Exception {
        // given — 이전 배치 실행 결과 시뮬레이션: baseDate 에 stale 데이터 직접 삽입
        LocalDate targetDate = LocalDate.of(2026, 4, 23);
        LocalDate baseDate = targetDate.minusDays(1);
        jdbcTemplate.update(
                "INSERT INTO mv_product_rank_monthly (product_id, base_date, `rank`, score, updated_at) VALUES (?, ?, ?, ?, ?)",
                1L, baseDate, 1, 5.0, LocalDateTime.now()
        );
        jobLauncherTestUtils.setJob(job);

        // when — metrics 없이 동일 targetDate 로 재실행
        var jobExecution = jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .toJobParameters());

        // then — stale 데이터가 제거되어 MV 에 0건
        assertAll(
                () -> assertThat(jobExecution.getExitStatus().getExitCode())
                        .isEqualTo(ExitStatus.COMPLETED.getExitCode()),
                () -> assertThat(countByBaseDate("mv_product_rank_monthly", baseDate)).isZero()
        );
    }

    @DisplayName("집계 대상 상품이 100개를 초과하더라도 MV 테이블에는 상위 100건만 적재된다.")
    @Test
    void limitsToTop100() throws Exception {
        // given — 101개 상품 데이터 삽입 (score 차별화: 각 상품마다 다른 order_amount)
        LocalDate targetDate = LocalDate.of(2026, 4, 22);
        LocalDateTime bucket = targetDate.minusDays(10).atTime(12, 0);
        for (long i = 1; i <= 101; i++) {
            insertMetrics(i, bucket, 0, 0, 0, (102 - i) * 100.0);
        }
        jobLauncherTestUtils.setJob(job);

        // when
        jobLauncherTestUtils.launchJob(new JobParametersBuilder()
                .addLocalDate("targetDate", targetDate)
                .toJobParameters());

        // then — MV 에는 정확히 100건만 존재
        LocalDate baseDate = targetDate.minusDays(1);
        int count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE base_date = ?",
                Integer.class, baseDate
        );
        assertThat(count).isEqualTo(100);
    }

    private void insertMetrics(Long productId, LocalDateTime bucketHour,
                               long viewCount, long likeCount, long orderCount, double orderAmount) {
        jdbcTemplate.update(
                "INSERT INTO product_metrics_hourly (product_id, bucket_hour, view_count, like_count, order_count, order_amount, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                productId, bucketHour, viewCount, likeCount, orderCount, orderAmount, LocalDateTime.now()
        );
    }

    private int countByBaseDate(String table, LocalDate baseDate) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `" + table + "` WHERE base_date = ?",
                Integer.class, baseDate
        );
    }
}
