package com.loopers.batch.job.cleanup;

import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
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
import java.time.LocalDate;
import java.time.temporal.IsoFields;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@Import(MySqlTestContainersConfig.class)
@TestPropertySource(properties = {
    "spring.batch.job.name=" + StagingCleanupJobConfig.JOB_NAME,
    "spring.batch.job.enabled=false"
})
class StagingCleanupJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(StagingCleanupJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly_staging");
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly_staging");
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly_staging");
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly_staging");
        jdbcTemplate.update("DELETE FROM mv_product_rank_weekly");
        jdbcTemplate.update("DELETE FROM mv_product_rank_monthly");
    }

    private void insertWeeklyStaging(String yearWeek, long productId) {
        jdbcTemplate.update(
            "INSERT INTO mv_product_rank_weekly_staging (year_week, product_id, score, updated_at) "
                + "VALUES (?, ?, ?, ?)",
            yearWeek, productId, 1.0, Timestamp.from(Instant.now())
        );
    }

    private void insertMonthlyStaging(String yearMonth, long productId) {
        jdbcTemplate.update(
            "INSERT INTO mv_product_rank_monthly_staging (year_month_key, product_id, score, updated_at) "
                + "VALUES (?, ?, ?, ?)",
            yearMonth, productId, 1.0, Timestamp.from(Instant.now())
        );
    }

    private String yearWeekOf(LocalDate date) {
        return String.format("%04d-W%02d",
            date.get(IsoFields.WEEK_BASED_YEAR),
            date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    private String yearMonthOf(LocalDate date) {
        return String.format("%04d-%02d", date.getYear(), date.getMonthValue());
    }

    private JobExecution runCleanupJob() throws Exception {
        return jobLauncherTestUtils.launchJob(
            new JobParametersBuilder()
                .addLong("run.id", System.currentTimeMillis())
                .toJobParameters()
        );
    }

    @Nested
    @DisplayName("Cleanup Job 실행 시, ")
    class WhenExecuted {

        @Test
        @DisplayName("4주 보존 경계 밖 주간 staging 과 3개월 보존 경계 밖 월간 staging 은 삭제된다.")
        void deletesDataBeyondRetentionWindow_keepsRecent() throws Exception {
            LocalDate today = LocalDate.now();
            String currentWeek = yearWeekOf(today);
            String expiredWeek = yearWeekOf(today.minusWeeks(5));
            String currentMonth = yearMonthOf(today);
            String expiredMonth = yearMonthOf(today.minusMonths(4));

            insertWeeklyStaging(currentWeek, 1L);
            insertWeeklyStaging(expiredWeek, 2L);
            insertMonthlyStaging(currentMonth, 10L);
            insertMonthlyStaging(expiredMonth, 20L);

            JobExecution exec = runCleanupJob();

            assertThat(exec.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

            Long currentWeeklyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly_staging WHERE year_week = ?",
                Long.class, currentWeek);
            Long expiredWeeklyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly_staging WHERE year_week = ?",
                Long.class, expiredWeek);
            assertThat(currentWeeklyCount).isEqualTo(1L);
            assertThat(expiredWeeklyCount).isEqualTo(0L);

            Long currentMonthlyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly_staging WHERE year_month_key = ?",
                Long.class, currentMonth);
            Long expiredMonthlyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly_staging WHERE year_month_key = ?",
                Long.class, expiredMonth);
            assertThat(currentMonthlyCount).isEqualTo(1L);
            assertThat(expiredMonthlyCount).isEqualTo(0L);
        }

        @Test
        @DisplayName("4주 전 주차 (oldest retained) 는 보존되고, 4주 경계 밖은 삭제된다.")
        void weeklyRetentionBoundary() throws Exception {
            LocalDate today = LocalDate.now();
            String oldestRetainedWeek = yearWeekOf(today.minusWeeks(3));   // 현재 주 포함 4주 보존의 가장 오래된 주
            String firstExpiredWeek = yearWeekOf(today.minusWeeks(4));     // 경계 밖

            insertWeeklyStaging(oldestRetainedWeek, 100L);
            insertWeeklyStaging(firstExpiredWeek, 101L);

            JobExecution exec = runCleanupJob();
            assertThat(exec.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

            Long retained = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly_staging WHERE year_week = ?",
                Long.class, oldestRetainedWeek);
            Long deleted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly_staging WHERE year_week = ?",
                Long.class, firstExpiredWeek);

            assertThat(retained).as("3주 전은 보존되어야 함").isEqualTo(1L);
            assertThat(deleted).as("4주 전은 경계 밖이므로 삭제되어야 함").isEqualTo(0L);
        }

        @Test
        @DisplayName("2개월 전은 보존되고, 3개월 전은 삭제된다.")
        void monthlyRetentionBoundary() throws Exception {
            LocalDate today = LocalDate.now();
            String oldestRetainedMonth = yearMonthOf(today.minusMonths(2));
            String firstExpiredMonth = yearMonthOf(today.minusMonths(3));

            insertMonthlyStaging(oldestRetainedMonth, 200L);
            insertMonthlyStaging(firstExpiredMonth, 201L);

            JobExecution exec = runCleanupJob();
            assertThat(exec.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

            Long retained = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly_staging WHERE year_month_key = ?",
                Long.class, oldestRetainedMonth);
            Long deleted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly_staging WHERE year_month_key = ?",
                Long.class, firstExpiredMonth);

            assertThat(retained).as("2개월 전은 보존되어야 함").isEqualTo(1L);
            assertThat(deleted).as("3개월 전은 경계 밖이므로 삭제되어야 함").isEqualTo(0L);
        }

        @Test
        @DisplayName("MV 테이블은 건드려지지 않는다 (staging 전용 cleanup).")
        void doesNotTouchMvTables() throws Exception {
            LocalDate today = LocalDate.now();
            String oldWeek = yearWeekOf(today.minusWeeks(10));
            String oldMonth = yearMonthOf(today.minusMonths(10));

            jdbcTemplate.update(
                "INSERT INTO mv_product_rank_weekly "
                    + "(year_week, product_id, ranking_position, score, created_at) "
                    + "VALUES (?, ?, ?, ?, ?)",
                oldWeek, 999L, 1, 10.0, Timestamp.from(Instant.now())
            );
            jdbcTemplate.update(
                "INSERT INTO mv_product_rank_monthly "
                    + "(year_month_key, product_id, ranking_position, score, created_at) "
                    + "VALUES (?, ?, ?, ?, ?)",
                oldMonth, 888L, 1, 20.0, Timestamp.from(Instant.now())
            );

            JobExecution exec = runCleanupJob();
            assertThat(exec.getExitStatus().getExitCode()).isEqualTo(ExitStatus.COMPLETED.getExitCode());

            Long weeklyMvCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly", Long.class);
            Long monthlyMvCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_monthly", Long.class);

            assertThat(weeklyMvCount).as("주간 MV 는 cleanup 대상이 아님").isEqualTo(1L);
            assertThat(monthlyMvCount).as("월간 MV 는 cleanup 대상이 아님").isEqualTo(1L);
        }
    }
}
