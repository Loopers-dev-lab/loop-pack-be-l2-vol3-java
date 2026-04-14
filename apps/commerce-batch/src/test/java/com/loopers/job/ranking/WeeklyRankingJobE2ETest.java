package com.loopers.job.ranking;

import com.loopers.batch.job.ranking.weekly.WeeklyRankingJobConfig;
import com.loopers.domain.ranking.MvProductRankWeeklyModel;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import org.junit.jupiter.api.BeforeEach;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME)
class WeeklyRankingJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(WeeklyRankingJobConfig.JOB_NAME)
    private org.springframework.batch.core.Job job;

    @Autowired
    private MvProductRankWeeklyJpaRepository weeklyJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        // commerce-batch에는 ProductMetricsModel 엔티티가 없어 Hibernate가 테이블을 생성하지 않으므로 직접 생성
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS product_metrics (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                product_id BIGINT NOT NULL UNIQUE,
                view_count BIGINT NOT NULL DEFAULT 0,
                like_count BIGINT NOT NULL DEFAULT 0,
                sales_count BIGINT NOT NULL DEFAULT 0,
                sales_amount BIGINT NOT NULL DEFAULT 0,
                version BIGINT NOT NULL DEFAULT 0,
                updated_at DATETIME(6) NOT NULL
            ) ENGINE=InnoDB
            """);
        jdbcTemplate.execute("DELETE FROM product_metrics");
        weeklyJpaRepository.deleteAll();
    }

    @DisplayName("product_metrics 데이터가 있으면 주간 랭킹 MV가 정상 적재된다")
    @Test
    void weeklyRankingJob_success() throws Exception {
        // given — product_metrics에 테스트 데이터 삽입
        jdbcTemplate.execute("""
            INSERT INTO product_metrics (product_id, view_count, like_count, sales_count, sales_amount, version, updated_at)
            VALUES
                (1, 100, 50, 10, 1000000, 0, NOW()),
                (2, 200, 30, 5, 500000, 0, NOW()),
                (3, 50, 100, 20, 2000000, 0, NOW())
            """);

        jobLauncherTestUtils.setJob(job);

        // when — 배치 실행
        var jobParameters = new JobParametersBuilder()
                .addString("requestDate", "20260412")
                .toJobParameters();
        var jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then — Job 성공 확인
        assertThat(jobExecution.getExitStatus().getExitCode())
                .isEqualTo(ExitStatus.COMPLETED.getExitCode());

        // then — MV 데이터 검증
        List<MvProductRankWeeklyModel> results = weeklyJpaRepository.findAll();
        assertAll(
                () -> assertThat(results).hasSize(3),
                () -> assertThat(results.get(0).getRanking()).isEqualTo(1),
                () -> assertThat(results.get(0).getScore())
                        .isGreaterThan(results.get(1).getScore()),  // 1등 점수 > 2등 점수
                () -> assertThat(results.get(0).getYearWeek()).isEqualTo("2026W15")
        );
    }

    @DisplayName("배치 재실행 시 기존 데이터가 삭제되고 새로 적재된다")
    @Test
    void weeklyRankingJob_rerun_replacesOldData() throws Exception {
        // given — product_metrics 데이터 삽입
        jdbcTemplate.execute("""
            INSERT INTO product_metrics (product_id, view_count, like_count, sales_count, sales_amount, version, updated_at)
            VALUES (1, 100, 50, 10, 1000000, 0, NOW())
            """);

        jobLauncherTestUtils.setJob(job);

        var jobParameters = new JobParametersBuilder()
                .addString("requestDate", "20260412")
                .addLong("run.id", 10L)
                .toJobParameters();

        // when — 1차 실행
        jobLauncherTestUtils.launchJob(jobParameters);

        // when — 2차 실행 (동일 requestDate)
        var jobParameters2 = new JobParametersBuilder()
                .addString("requestDate", "20260412")
                .addLong("run.id", 11L)
                .toJobParameters();
        var jobExecution2 = jobLauncherTestUtils.launchJob(jobParameters2);

        // then — 중복 없이 1건만 존재
        assertThat(jobExecution2.getExitStatus().getExitCode())
                .isEqualTo(ExitStatus.COMPLETED.getExitCode());
        assertThat(weeklyJpaRepository.findAll()).hasSize(1);
    }
}
