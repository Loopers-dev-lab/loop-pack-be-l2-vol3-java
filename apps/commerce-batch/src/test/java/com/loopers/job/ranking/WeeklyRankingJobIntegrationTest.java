package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 주간 랭킹 배치 통합 테스트.
 *
 * 검증 항목:
 * - Happy path: 여러 이벤트를 타입별 가중치로 집계해 TOP 100을 MV에 적재
 * - Tie-break: 동일 score는 product_id ASC 순서
 * - Idempotency: 같은 targetDate로 2회 실행 시 MV row 동일
 * - Period isolation: W16 배치가 W15 기존 데이터를 건드리지 않음
 */
@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME)
class WeeklyRankingJobIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(WeeklyRankingJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        jobLauncherTestUtils = new JobLauncherTestUtils();
        jobLauncherTestUtils.setJob(job);
        jobLauncherTestUtils.setJobLauncher(jobLauncher);
        jobLauncherTestUtils.setJobRepository(jobRepository);

        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.update("DELETE FROM ranking_event");
    }

    @DisplayName("happy path — 여러 이벤트를 가중치 공식으로 집계해 TOP N을 MV에 적재한다")
    @Test
    void happyPath() throws Exception {
        // arrange: 2026-W16 (2026-04-13 ~ 2026-04-19) 범위에 이벤트 시드
        //   product 1: view 10 (×0.1) + like 5 (×0.2) = 1.0 + 1.0 = 2.0 → rank 1
        //   product 2: order 3 (×0.6) = 1.8                              → rank 2
        //   product 3: view 1 (×0.1) = 0.1                                → rank 3
        ZonedDateTime eventTime = LocalDate.of(2026, 4, 15).atStartOfDay(KST);
        for (int i = 0; i < 10; i++) {
            seedEvent("ob-p1-v" + i, 1L, "VIEW", eventTime);
        }
        for (int i = 0; i < 5; i++) {
            seedEvent("ob-p1-l" + i, 1L, "LIKE", eventTime);
        }
        for (int i = 0; i < 3; i++) {
            seedEvent("ob-p2-o" + i, 2L, "ORDER", eventTime);
        }
        seedEvent("ob-p3-v1", 3L, "VIEW", eventTime);

        // act
        JobExecution exec = runJob("20260415", 1L);

        // assert
        assertThat(exec.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

        List<MvRow> rows = fetchMv("2026-W16");
        assertThat(rows).hasSize(3);

        assertThat(rows.get(0).rankNo).isEqualTo(1);
        assertThat(rows.get(0).productId).isEqualTo(1L);
        assertThat(rows.get(0).score).isEqualTo(2.0, within(0.0001));

        assertThat(rows.get(1).rankNo).isEqualTo(2);
        assertThat(rows.get(1).productId).isEqualTo(2L);
        assertThat(rows.get(1).score).isEqualTo(1.8, within(0.0001));

        assertThat(rows.get(2).rankNo).isEqualTo(3);
        assertThat(rows.get(2).productId).isEqualTo(3L);
        assertThat(rows.get(2).score).isEqualTo(0.1, within(0.0001));
    }

    @DisplayName("tie-break — 동일 score는 product_id ASC 순서로 랭크된다")
    @Test
    void tieBreak() throws Exception {
        // arrange: product 5, 2, 9 모두 주문 1건씩 = 각 0.6점
        ZonedDateTime eventTime = LocalDate.of(2026, 4, 15).atStartOfDay(KST);
        seedEvent("ob-p5", 5L, "ORDER", eventTime);
        seedEvent("ob-p2", 2L, "ORDER", eventTime);
        seedEvent("ob-p9", 9L, "ORDER", eventTime);

        // act
        runJob("20260415", 10L);

        // assert: product_id ASC = 2, 5, 9
        List<MvRow> rows = fetchMv("2026-W16");
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).productId).isEqualTo(2L);
        assertThat(rows.get(0).rankNo).isEqualTo(1);
        assertThat(rows.get(1).productId).isEqualTo(5L);
        assertThat(rows.get(1).rankNo).isEqualTo(2);
        assertThat(rows.get(2).productId).isEqualTo(9L);
        assertThat(rows.get(2).rankNo).isEqualTo(3);
    }

    @DisplayName("idempotency — 같은 targetDate 2회 실행 시 MV row 수·내용이 동일하다")
    @Test
    void idempotent() throws Exception {
        // arrange
        ZonedDateTime eventTime = LocalDate.of(2026, 4, 15).atStartOfDay(KST);
        for (int i = 0; i < 3; i++) {
            seedEvent("ob-p1-o" + i, 1L, "ORDER", eventTime);
        }
        seedEvent("ob-p2-v1", 2L, "VIEW", eventTime);

        // act: 두 번 실행 (run.id로 JobInstance 유일성 확보)
        runJob("20260415", 100L);
        List<MvRow> firstRun = fetchMv("2026-W16");

        runJob("20260415", 101L);
        List<MvRow> secondRun = fetchMv("2026-W16");

        // assert
        assertThat(secondRun).hasSize(firstRun.size());
        for (int i = 0; i < firstRun.size(); i++) {
            assertThat(secondRun.get(i).productId).isEqualTo(firstRun.get(i).productId);
            assertThat(secondRun.get(i).rankNo).isEqualTo(firstRun.get(i).rankNo);
            assertThat(secondRun.get(i).score).isEqualTo(firstRun.get(i).score, within(0.0001));
        }
    }

    @DisplayName("period isolation — 다른 주차 기존 데이터는 건드리지 않는다")
    @Test
    void periodIsolation() throws Exception {
        // arrange: W15 기존 row를 수동으로 MV에 삽입
        jdbcTemplate.update("""
                INSERT INTO mv_product_rank_weekly
                    (year_week, rank_no, product_id, score,
                     period_start, period_end, is_finalized, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "2026-W15", 1, 999L, 50.0,
                java.sql.Date.valueOf(LocalDate.of(2026, 4, 6)),
                java.sql.Date.valueOf(LocalDate.of(2026, 4, 12)),
                true,
                Timestamp.from(ZonedDateTime.now(KST).toInstant()));

        // 새 배치는 W16 대상
        ZonedDateTime eventTime = LocalDate.of(2026, 4, 15).atStartOfDay(KST);
        seedEvent("ob-p1-o1", 1L, "ORDER", eventTime);

        // act
        runJob("20260415", 200L);

        // assert: W15 row 변하지 않음
        List<MvRow> w15 = fetchMv("2026-W15");
        assertThat(w15).hasSize(1);
        assertThat(w15.get(0).productId).isEqualTo(999L);
        assertThat(w15.get(0).score).isEqualTo(50.0, within(0.0001));

        // W16는 새로 생성됨
        List<MvRow> w16 = fetchMv("2026-W16");
        assertThat(w16).hasSize(1);
        assertThat(w16.get(0).productId).isEqualTo(1L);
    }

    // ---------- helpers ----------

    private void seedEvent(String outboxId, long productId, String eventType, ZonedDateTime eventTime) {
        jdbcTemplate.update(
                """
                INSERT INTO ranking_event
                    (outbox_id, product_id, event_type, event_time, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                outboxId,
                productId,
                eventType,
                Timestamp.from(eventTime.toInstant()),
                Timestamp.from(ZonedDateTime.now(KST).toInstant()));
    }

    private JobExecution runJob(String targetDate, long runId) throws Exception {
        return jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addString("targetDate", targetDate)
                        .addLong("run.id", runId)
                        .toJobParameters()
        );
    }

    private List<MvRow> fetchMv(String yearWeek) {
        return jdbcTemplate.query(
                """
                SELECT rank_no, product_id, score, is_finalized
                FROM mv_product_rank_weekly
                WHERE year_week = ?
                ORDER BY rank_no ASC
                """,
                (rs, rowNum) -> new MvRow(
                        rs.getInt("rank_no"),
                        rs.getLong("product_id"),
                        rs.getDouble("score"),
                        rs.getBoolean("is_finalized")),
                yearWeek);
    }

    private record MvRow(int rankNo, long productId, double score, boolean isFinalized) {}
}
