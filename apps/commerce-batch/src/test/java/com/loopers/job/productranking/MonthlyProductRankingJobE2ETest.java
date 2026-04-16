package com.loopers.job.productranking;


import com.loopers.batch.job.productranking.monthly.MonthlyProductRankingJobConfig;
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
 * MonthlyProductRankingJob E2E 테스트
 * - product_metrics(일간 여러 날) + product_read_model 직접 적재 후 Job 실행
 * - staging → mv_product_rank_monthly 교체 검증
 */
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + MonthlyProductRankingJobConfig.JOB_NAME)
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("MonthlyProductRankingJob E2E 테스트")
class MonthlyProductRankingJobE2ETest {

	@Autowired
	private JobLauncherTestUtils jobLauncherTestUtils;

	@Autowired
	@Qualifier(MonthlyProductRankingJobConfig.JOB_NAME)
	private Job job;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final LocalDate TARGET_DATE = LocalDate.of(2024, 4, 15);


	@BeforeEach
	void setUp() {
		jobLauncherTestUtils.setJob(job);
		ProductRankingBatchSchemaSupport.createTables(jdbcTemplate);

		jdbcTemplate.execute("DELETE FROM mv_product_rank_monthly");
		jdbcTemplate.execute("DELETE FROM stg_product_rank_monthly");
		jdbcTemplate.execute("DELETE FROM product_metrics");
		jdbcTemplate.execute("DELETE FROM product_read_model");
		jdbcTemplate.execute("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT");
		jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT");
		jdbcTemplate.execute("DELETE FROM BATCH_STEP_EXECUTION");
		jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION_PARAMS");
		jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION");
		jdbcTemplate.execute("DELETE FROM BATCH_JOB_INSTANCE");

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

		// product_metrics — 같은 달 여러 날 적재 (월간 SUM 검증)
		LocalDate[] dates = {
			LocalDate.of(2024, 4, 1),
			LocalDate.of(2024, 4, 15),
			LocalDate.of(2024, 4, 30)
		};
		for (LocalDate date : dates) {
			jdbcTemplate.update(
				"INSERT INTO product_metrics (metric_date, product_id, view_count, like_count, sales_count, version, updated_at) " +
				"VALUES (?, ?, ?, ?, ?, ?, NOW())",
				date, 1L, 30L, 5L, 2L, 1L
			);
			jdbcTemplate.update(
				"INSERT INTO product_metrics (metric_date, product_id, view_count, like_count, sales_count, version, updated_at) " +
				"VALUES (?, ?, ?, ?, ?, ?, NOW())",
				date, 2L, 10L, 1L, 0L, 1L
			);
		}
	}


	@Test
	@DisplayName("[monthlyProductRankingJob] 정상 실행 -> COMPLETED. mv_product_rank_monthly에 TOP 2 적재, month_start_date = 2024-04-01")
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
			"SELECT COUNT(*) FROM mv_product_rank_monthly WHERE scorer_type = 'SATURATION'",
			Integer.class
		);
		assertThat(mvCount).isEqualTo(2);

		// Assert — month_start_date = 2024-04-01
		LocalDate monthStart = jdbcTemplate.queryForObject(
			"SELECT month_start_date FROM mv_product_rank_monthly WHERE rank_position = 1 AND scorer_type = 'SATURATION'",
			LocalDate.class
		);
		assertThat(monthStart).isEqualTo(LocalDate.of(2024, 4, 1));

		// Assert — month_key = '2024-04'
		String monthKey = jdbcTemplate.queryForObject(
			"SELECT month_key FROM mv_product_rank_monthly WHERE rank_position = 1 AND scorer_type = 'SATURATION'",
			String.class
		);
		assertThat(monthKey).isEqualTo("2024-04");

		// Assert — 1위는 product 1 (3일 합산 score가 더 높음)
		Long firstProductId = jdbcTemplate.queryForObject(
			"SELECT product_id FROM mv_product_rank_monthly WHERE rank_position = 1 AND scorer_type = 'SATURATION'",
			Long.class
		);
		assertThat(firstProductId).isEqualTo(1L);

		// Assert — staging 정리 완료
		int stagingCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM stg_product_rank_monthly",
			Integer.class
		);
		assertThat(stagingCount).isEqualTo(0);
	}


	@Test
	@DisplayName("[monthlyProductRankingJob] 재실행 시 MV 교체 -> 기존 MV 삭제 후 새 데이터로 교체")
	void shouldReplaceExistingMv_onRerun() throws Exception {
		// Arrange — 1차 실행
		var firstParams = new JobParametersBuilder()
			.addString("targetDate", TARGET_DATE.format(DATE_FMT))
			.addString("scorerType", "SATURATION")
			.toJobParameters();
		jobLauncherTestUtils.launchJob(firstParams);

		int firstCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM mv_product_rank_monthly WHERE scorer_type = 'SATURATION'",
			Integer.class
		);

		// Arrange — 배치 실행 기록 초기화 후 2차 재실행
		jdbcTemplate.execute("DELETE FROM BATCH_STEP_EXECUTION_CONTEXT");
		jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION_CONTEXT");
		jdbcTemplate.execute("DELETE FROM BATCH_STEP_EXECUTION");
		jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION_PARAMS");
		jdbcTemplate.execute("DELETE FROM BATCH_JOB_EXECUTION");
		jdbcTemplate.execute("DELETE FROM BATCH_JOB_INSTANCE");

		var secondParams = new JobParametersBuilder()
			.addString("targetDate", TARGET_DATE.format(DATE_FMT))
			.addString("scorerType", "SATURATION")
			.toJobParameters();

		// Act
		JobExecution secondExecution = jobLauncherTestUtils.launchJob(secondParams);

		// Assert — 2차 실행도 COMPLETED
		assertThat(secondExecution.getExitStatus().getExitCode())
			.isEqualTo(ExitStatus.COMPLETED.getExitCode());

		// Assert — MV row 수는 1차와 동일 (교체 O, 누적 X)
		int secondCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM mv_product_rank_monthly WHERE scorer_type = 'SATURATION'",
			Integer.class
		);
		assertAll(
			() -> assertThat(firstCount).isEqualTo(2),
			() -> assertThat(secondCount).isEqualTo(2) // 누적 X, 교체 O
		);
	}

}
