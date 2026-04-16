package com.loopers.batch.job.ranking;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@Import(MySqlTestContainersConfig.class)
@TestPropertySource(properties = {
    "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
class WeeklyRankingJobE2ETest {

    private static final String YEAR_WEEK = "2026-W15";
    // 2026-W15 = 2026-04-06 (Mon) ~ 2026-04-12 (Sun)

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(WeeklyRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly_staging");
        jdbcTemplate.update("DELETE FROM ranking_score_ledger");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly_staging");
        jdbcTemplate.update("DELETE FROM ranking_score_ledger");
    }

    private JobParameters params(String yearWeek) {
        return new JobParametersBuilder()
            .addString("year_week", yearWeek)
            .addLong("run.id", System.currentTimeMillis())
            .toJobParameters();
    }

    private void insertLedger(long productId, String bucketKey, double basePoints) {
        jdbcTemplate.update(
            "INSERT INTO ranking_score_ledger "
                + "(bucket_type, bucket_key, product_id, base_points, last_scored_at, dirty, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            "DAY", bucketKey, productId, basePoints,
            Timestamp.from(Instant.now()), false,
            Timestamp.from(Instant.now()), Timestamp.from(Instant.now())
        );
    }

    @Nested
    @DisplayName("주간 랭킹 Job 을 정상 실행할 때, ")
    class HappyPath {

        @Test
        void sumsDailyBasePointsAndInsertsTopNIntoMv() throws Exception {
            // given: 3개 상품, 7일치 ledger
            // product 1: 매일 1.0 → 주간 7.0
            // product 2: 매일 2.0 → 주간 14.0
            // product 3: 매일 0.5 → 주간 3.5
            String[] days = {"20260406", "20260407", "20260408", "20260409", "20260410", "20260411", "20260412"};
            for (String day : days) {
                insertLedger(1L, day, 1.0);
                insertLedger(2L, day, 2.0);
                insertLedger(3L, day, 0.5);
            }

            // when
            JobExecution exec = jobLauncherTestUtils.launchJob(params(YEAR_WEEK));

            // then
            assertThat(exec.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

            List<Map<String, Object>> mvRows = jdbcTemplate.queryForList(
                "SELECT product_id, ranking_position, score FROM mv_product_rank_weekly "
                    + "WHERE year_week = ? ORDER BY ranking_position", YEAR_WEEK);

            assertThat(mvRows).hasSize(3);
            assertThat(mvRows.get(0)).containsEntry("product_id", 2L);
            assertThat(mvRows.get(0)).containsEntry("ranking_position", 1L);
            assertThat(((Number) mvRows.get(0).get("score")).doubleValue()).isEqualTo(14.0);

            assertThat(mvRows.get(1)).containsEntry("product_id", 1L);
            assertThat(mvRows.get(1)).containsEntry("ranking_position", 2L);
            assertThat(((Number) mvRows.get(1).get("score")).doubleValue()).isEqualTo(7.0);

            assertThat(mvRows.get(2)).containsEntry("product_id", 3L);
            assertThat(mvRows.get(2)).containsEntry("ranking_position", 3L);
            assertThat(((Number) mvRows.get(2).get("score")).doubleValue()).isEqualTo(3.5);
        }

        @Test
        void filtersOutLedgerRowsOutsideWeekRange() throws Exception {
            // given: 해당 주 데이터 + 주 범위 밖 데이터 섞어 주입
            insertLedger(1L, "20260406", 5.0);   // 주간 포함
            insertLedger(1L, "20260405", 100.0); // 이전 주 (일)
            insertLedger(1L, "20260413", 200.0); // 다음 주 (월)

            // when
            JobExecution exec = jobLauncherTestUtils.launchJob(params(YEAR_WEEK));

            // then
            assertThat(exec.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

            Double score = jdbcTemplate.queryForObject(
                "SELECT score FROM mv_product_rank_weekly WHERE year_week = ? AND product_id = ?",
                Double.class, YEAR_WEEK, 1L);
            assertThat(score).isEqualTo(5.0);
        }

        @Test
        void limitsMvRowsToTop100_whenManyProducts() throws Exception {
            // given: 150 개 상품, 각자 고유 score
            for (long pid = 1; pid <= 150; pid++) {
                insertLedger(pid, "20260406", (double) pid);
            }

            // when
            JobExecution exec = jobLauncherTestUtils.launchJob(params(YEAR_WEEK));

            // then
            assertThat(exec.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

            Long mvCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE year_week = ?",
                Long.class, YEAR_WEEK);
            assertThat(mvCount).isEqualTo(100L);

            // rank 1 은 score 가 가장 큰 150번 상품
            Map<String, Object> first = jdbcTemplate.queryForMap(
                "SELECT product_id, ranking_position FROM mv_product_rank_weekly "
                    + "WHERE year_week = ? AND ranking_position = 1", YEAR_WEEK);
            assertThat(first).containsEntry("product_id", 150L);

            // ranking_position 은 1~100 연속이어야 함
            Long minPos = jdbcTemplate.queryForObject(
                "SELECT MIN(ranking_position) FROM mv_product_rank_weekly WHERE year_week = ?",
                Long.class, YEAR_WEEK);
            Long maxPos = jdbcTemplate.queryForObject(
                "SELECT MAX(ranking_position) FROM mv_product_rank_weekly WHERE year_week = ?",
                Long.class, YEAR_WEEK);
            assertThat(minPos).isEqualTo(1L);
            assertThat(maxPos).isEqualTo(100L);
        }

        @Test
        @DisplayName("score 동점 시 product_id ASC 로 tie-break 되어 순위가 결정적이다.")
        void tieBreakByProductIdAsc() throws Exception {
            // given: 세 상품이 완전히 동일한 score (같은 bucket_key 에 같은 base_points)
            insertLedger(30L, "20260406", 5.0);
            insertLedger(10L, "20260406", 5.0);
            insertLedger(20L, "20260406", 5.0);

            // when
            JobExecution exec = jobLauncherTestUtils.launchJob(params(YEAR_WEEK));
            assertThat(exec.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

            // then: product_id 작은 순으로 1, 2, 3
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT product_id, ranking_position FROM mv_product_rank_weekly "
                    + "WHERE year_week = ? ORDER BY ranking_position", YEAR_WEEK);
            assertThat(rows).hasSize(3);
            assertThat(rows.get(0)).containsEntry("product_id", 10L);
            assertThat(rows.get(1)).containsEntry("product_id", 20L);
            assertThat(rows.get(2)).containsEntry("product_id", 30L);
        }
    }

    @Nested
    @DisplayName("같은 주차로 Job 을 두 번 실행해도, ")
    class Idempotency {

        @Test
        void doesNotDoubleCountScores() throws Exception {
            // given
            insertLedger(1L, "20260406", 5.0);
            insertLedger(1L, "20260407", 3.0);

            // when: 첫 실행
            JobExecution first = jobLauncherTestUtils.launchJob(params(YEAR_WEEK));
            assertThat(first.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

            Double firstScore = jdbcTemplate.queryForObject(
                "SELECT score FROM mv_product_rank_weekly WHERE year_week = ? AND product_id = ?",
                Double.class, YEAR_WEEK, 1L);
            assertThat(firstScore).isEqualTo(8.0);

            // when: 두 번째 실행 (새 run.id)
            JobExecution second = jobLauncherTestUtils.launchJob(params(YEAR_WEEK));
            assertThat(second.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

            // then: score 는 여전히 8.0 이어야 함 (2배 되지 않음)
            Double secondScore = jdbcTemplate.queryForObject(
                "SELECT score FROM mv_product_rank_weekly WHERE year_week = ? AND product_id = ?",
                Double.class, YEAR_WEEK, 1L);
            assertThat(secondScore).isEqualTo(8.0);
        }

        @Test
        void doesNotAffectOtherWeeksData() throws Exception {
            // given: 2026-W14, 2026-W15 의 MV 데이터가 모두 있음
            jdbcTemplate.update(
                "INSERT INTO mv_product_rank_weekly "
                    + "(year_week, product_id, ranking_position, score, created_at) "
                    + "VALUES (?, ?, ?, ?, ?)",
                "2026-W14", 999L, 1, 100.0, Timestamp.from(Instant.now())
            );
            insertLedger(1L, "20260406", 5.0);

            // when: 2026-W15 Job 실행
            JobExecution exec = jobLauncherTestUtils.launchJob(params(YEAR_WEEK));
            assertThat(exec.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

            // then: 2026-W14 데이터는 건드려지지 않아야 함
            Long w14Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE year_week = '2026-W14'",
                Long.class);
            assertThat(w14Count).isEqualTo(1L);

            Long w15Count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE year_week = '2026-W15'",
                Long.class);
            assertThat(w15Count).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("ledger 에 해당 주차 데이터가 하나도 없을 때, ")
    class EmptySource {

        @Test
        void completesWithoutError_andProducesNoMvRows() throws Exception {
            // given: 아무 것도 없음

            // when
            JobExecution exec = jobLauncherTestUtils.launchJob(params(YEAR_WEEK));

            // then
            assertThat(exec.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());
            Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE year_week = ?",
                Long.class, YEAR_WEEK);
            assertThat(count).isEqualTo(0L);
        }
    }
}
