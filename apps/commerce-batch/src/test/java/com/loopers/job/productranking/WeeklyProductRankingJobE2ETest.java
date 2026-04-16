package com.loopers.job.productranking;


import com.loopers.batch.job.productranking.weekly.WeeklyProductRankingJobConfig;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


/**
 * WeeklyProductRankingJob E2E 테스트
 * - product_metrics(일간) + product_read_model 데이터 직접 적재 후 Job 실행
 * - staging → mv_product_rank_weekly 교체 검증
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + WeeklyProductRankingJobConfig.JOB_NAME)
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("WeeklyProductRankingJob E2E 테스트")
class WeeklyProductRankingJobE2ETest {

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	@Qualifier(WeeklyProductRankingJobConfig.JOB_NAME)
	private Job job;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final LocalDate TARGET_DATE = LocalDate.of(2024, 4, 15); // 월요일 포함 주


	@BeforeEach
	void setUp() {
		jobLauncherTestUtils.setJob(job);
		ProductRankingBatchSchemaSupport.createTables(jdbcTemplate);

		// 테이블 초기화
		jdbcTemplate.execute("DELETE FROM mv_product_rank_weekly");
		jdbcTemplate.execute("DELETE FROM stg_product_rank_weekly");
		jdbcTemplate.execute("DELETE FROM product_metrics");
		jdbcTemplate.execute("DELETE FROM product_read_model");
		jdbcTemplate.execute("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT");
		jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT");
		jdbcTemplate.execute("DELETE FROM BATCH_STEP_EXECUTION");
		jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION_PARAMS");
		jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION");
		jdbcTemplate.execute("DELETE FROM BATCH_JOB_INSTANCE");

		// product_read_model 데이터 삽입 (brand 없이 직접)
		jdbcTemplate.update(
			"INSERT INTO product_read_model (id, brand_id, brand_name, name, price, stock, description, like_count, metrics_version, created_at, updated_at) " +
			"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
			1L, 1L, "나이키", "에어맥스", new BigDecimal("129000"), 100L, "러닝화", 0L, 0L
		);
		jdbcTemplate.update(
			"INSERT INTO product_read_model (id, brand_id, brand_name, name, price, stock, description, like_count, metrics_version, created_at, updated_at) " +
			"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
			2L, 1L, "나이키", "에어포스", new BigDecimal("139000"), 50L, "캐주얼화", 0L, 0L
		);

		// product_metrics 일간 데이터 삽입 (targetDate 주 범위 내)
		// product 1: view=100, like=20, sales=5
		jdbcTemplate.update(
			"INSERT INTO product_metrics (metric_date, product_id, view_count, like_count, sales_count, version, updated_at) " +
			"VALUES (?, ?, ?, ?, ?, ?, NOW())",
			TARGET_DATE, 1L, 100L, 20L, 5L, 1L
		);
		// product 2: view=50, like=5, sales=1
		jdbcTemplate.update(
			"INSERT INTO product_metrics (metric_date, product_id, view_count, like_count, sales_count, version, updated_at) " +
			"VALUES (?, ?, ?, ?, ?, ?, NOW())",
			TARGET_DATE, 2L, 50L, 5L, 1L, 1L
		);
	}


	@Test
	@DisplayName("[weeklyProductRankingJob] 정상 실행 -> COMPLETED. mv_product_rank_weekly에 TOP 2 적재")
	void shouldCompleteJob_andPublishToMv() throws Exception {
		// Arrange
		var jobParameters = new JobParametersBuilder()
			.addString("targetDate", TARGET_DATE.format(DATE_FMT))
			.addString("scorerType", "SATURATION")
			.toJobParameters();

		// Act
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

		// Assert — Job 상태
		assertThat(jobExecution.getExitStatus().getExitCode())
			.isEqualTo(ExitStatus.COMPLETED.getExitCode());

		// Assert — MV 적재 결과
		int mvCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM mv_product_rank_weekly WHERE scorer_type = 'SATURATION'",
			Integer.class
		);
		assertThat(mvCount).isEqualTo(2);

		// Assert — 1위는 product 1 (score가 더 높음)
		Long firstProductId = jdbcTemplate.queryForObject(
			"SELECT product_id FROM mv_product_rank_weekly WHERE rank_position = 1 AND scorer_type = 'SATURATION'",
			Long.class
		);
		assertThat(firstProductId).isEqualTo(1L);

		// Assert — staging 정리 완료
		int stagingCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM stg_product_rank_weekly",
			Integer.class
		);
		assertThat(stagingCount).isEqualTo(0);
	}


	@Test
	@DisplayName("[weeklyProductRankingJob] product_metrics 비어있음 -> COMPLETED. mv_product_rank_weekly 비어있음")
	void shouldCompleteJob_withEmptyMetrics() throws Exception {
		// Arrange — product_metrics 비우기
		jdbcTemplate.execute("DELETE FROM product_metrics");

		var jobParameters = new JobParametersBuilder()
			.addString("targetDate", TARGET_DATE.format(DATE_FMT))
			.addString("scorerType", "SATURATION")
			.toJobParameters();

		// Act
		JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

		// Assert
		assertAll(
			() -> assertThat(jobExecution.getExitStatus().getExitCode())
				.isEqualTo(ExitStatus.COMPLETED.getExitCode()),
			() -> assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM mv_product_rank_weekly WHERE scorer_type = 'SATURATION'",
				Integer.class
			)).isEqualTo(0)
		);
	}


	@Test
	@DisplayName("[weeklyProductRankingJob] targetDate 미전달 -> FAILED")
	void shouldFailJob_whenTargetDateIsMissing() throws Exception {
		// Act
		JobExecution jobExecution = jobLauncherTestUtils.launchJob();

		// Assert
		assertThat(jobExecution.getExitStatus().getExitCode())
			.isEqualTo(ExitStatus.FAILED.getExitCode());
	}

}
