package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.domain.ranking.audit.BatchAuditLog;
import com.loopers.domain.ranking.audit.BatchAuditLogRepository;
import com.loopers.domain.ranking.mv.MvProductRankLast30dRepository;
import com.loopers.domain.ranking.mv.MvProductRankLast7dRepository;
import com.loopers.domain.ranking.weight.WeightConfig;
import com.loopers.domain.ranking.weight.WeightConfigRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 랭킹 배치 전체 파이프라인 E2E — Step 0 ~ Step 6 까지의 통과 검증.
 * 원천 3개 테이블에 시드 → Job 실행 → MV + audit_log + Redis ZSET 결과 검증.
 */
@SpringBootTest
@SpringBatchTest
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@TestPropertySource(properties = "spring.batch.job.name=" + RollingRankingJobConfig.JOB_NAME)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RollingRankingJobE2ETest {

    private static final String ANCHOR_KEY = "20260414";
    private static final LocalDate ANCHOR = LocalDate.of(2026, 4, 14);
    private static final LocalDateTime IN_7D = LocalDateTime.of(2026, 4, 10, 12, 0);

    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired @Qualifier(RollingRankingJobConfig.JOB_NAME) private Job job;
    @Autowired private WeightConfigRepository weightConfigRepository;
    @Autowired private MvProductRankLast7dRepository last7dRepository;
    @Autowired private MvProductRankLast30dRepository last30dRepository;
    @Autowired private BatchAuditLogRepository auditLogRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RedisTemplate<String, String> redisTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @Nested
    class 전체_파이프라인 {

        @Test
        void 원천에서_MV_audit_Redis_ZSET_까지_전체_파이프라인이_성공하고_identity_cache_가_된다() throws Exception {
            weightConfigRepository.save(new WeightConfig("control", 0.1, 0.2, 0.7, 100, true));

            // 3 product, 각각 다른 원천
            saveView(1L, IN_7D, 100);  saveLike(1L, IN_7D, 50);  saveOrder(1L, IN_7D, 999);
            saveView(2L, IN_7D, 50);   saveLike(2L, IN_7D, 10);  saveOrder(2L, IN_7D, 100);
            saveView(3L, IN_7D, 10);   saveLike(3L, IN_7D, 2);   saveOrder(3L, IN_7D, 10);

            jobLauncherTestUtils.setJob(job);
            JobExecution execution = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR_KEY));

            // MV 에 TOP N (여기선 3 상품) 적재 + rank 1,2,3 연속
            // Redis ZSET 에 같은 score 순으로 적재
            Set<ZSetOperations.TypedTuple<String>> zsetLast7d = redisTemplate.opsForZSet()
                    .reverseRangeWithScores("ranking:last7d:" + ANCHOR_KEY + ":control", 0, -1);

            assertAll(
                    () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                    () -> assertThat(last7dRepository.countByAnchorDate(ANCHOR)).isEqualTo(3L),
                    () -> assertThat(last30dRepository.countByAnchorDate(ANCHOR)).isEqualTo(3L),
                    () -> assertThat(rankPositions("mv_product_rank_last_7d", "control")).containsExactly(1, 2, 3),
                    // audit 로그 2건 (LAST_7D + LAST_30D)
                    () -> assertThat(auditLogRepository.findByAnchorDate(ANCHOR))
                            .extracting(BatchAuditLog::getStatus)
                            .containsOnly(BatchAuditLog.STATUS_OK),
                    // Redis ZSET 에 동일 3 상품이 동일 score 로 들어감 (identity cache)
                    () -> assertThat(zsetLast7d).hasSize(3)
            );
        }

        @Test
        void 여러_weight_group_이_활성화되면_MV_Redis_모두_그룹별로_독립_생성된다() throws Exception {
            weightConfigRepository.save(new WeightConfig("control",      0.1, 0.2, 0.7, 50, true));
            weightConfigRepository.save(new WeightConfig("experiment_a", 0.8, 0.1, 0.1, 50, true));

            saveView(1L, IN_7D, 100);
            saveLike(1L, IN_7D, 50);
            saveOrder(1L, IN_7D, 500);

            jobLauncherTestUtils.setJob(job);
            JobExecution execution = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR_KEY));

            assertAll(
                    () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                    // control + experiment_a 두 그룹 × 1 상품 = 2 row per MV
                    () -> assertThat(last7dRepository.countByAnchorDate(ANCHOR)).isEqualTo(2L),
                    () -> assertThat(redisTemplate.hasKey("ranking:last7d:" + ANCHOR_KEY + ":control")).isTrue(),
                    () -> assertThat(redisTemplate.hasKey("ranking:last7d:" + ANCHOR_KEY + ":experiment_a")).isTrue(),
                    () -> assertThat(redisTemplate.hasKey("ranking:last30d:" + ANCHOR_KEY + ":control")).isTrue(),
                    () -> assertThat(redisTemplate.hasKey("ranking:last30d:" + ANCHOR_KEY + ":experiment_a")).isTrue()
            );
        }
    }

    @Nested
    class 멱등성 {

        @Test
        void 같은_anchorDate_로_두번_돌려도_최종_결과가_동일하다() throws Exception {
            weightConfigRepository.save(new WeightConfig("control", 0.1, 0.2, 0.7, 100, true));
            saveView(1L, IN_7D, 100);
            saveLike(1L, IN_7D, 50);
            saveOrder(1L, IN_7D, 999);

            jobLauncherTestUtils.setJob(job);
            jobLauncherTestUtils.launchJob(paramsOf(ANCHOR_KEY));
            double firstScore = scoreOfMv("mv_product_rank_last_7d", ANCHOR_KEY, "control", 1L);

            JobExecution second = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR_KEY));
            double secondScore = scoreOfMv("mv_product_rank_last_7d", ANCHOR_KEY, "control", 1L);

            assertAll(
                    () -> assertThat(second.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                    () -> assertThat(last7dRepository.countByAnchorDate(ANCHOR)).isEqualTo(1L),
                    () -> assertThat(secondScore).isEqualTo(firstScore)
            );
        }
    }

    @Nested
    class 빈_원천 {

        @Test
        void 원천이_비어_있어도_Job_은_성공한다() throws Exception {
            weightConfigRepository.save(new WeightConfig("control", 0.1, 0.2, 0.7, 100, true));

            jobLauncherTestUtils.setJob(job);
            JobExecution execution = jobLauncherTestUtils.launchJob(paramsOf(ANCHOR_KEY));

            assertAll(
                    () -> assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED),
                    () -> assertThat(last7dRepository.countByAnchorDate(ANCHOR)).isZero(),
                    () -> assertThat(last30dRepository.countByAnchorDate(ANCHOR)).isZero(),
                    () -> assertThat(redisTemplate.hasKey("ranking:last7d:" + ANCHOR_KEY + ":control")).isFalse()
            );
        }
    }

    // -- helpers --

    private void saveView(long productId, LocalDateTime bucketTime, long viewCount) {
        jdbcTemplate.update(
                "INSERT INTO product_view_metrics (product_id, bucket_time, view_count) VALUES (?, ?, ?)",
                productId, Timestamp.valueOf(bucketTime), viewCount);
    }

    private void saveLike(long productId, LocalDateTime bucketTime, long likeCount) {
        jdbcTemplate.update(
                "INSERT INTO product_like_metrics (product_id, bucket_time, like_count) VALUES (?, ?, ?)",
                productId, Timestamp.valueOf(bucketTime), likeCount);
    }

    private void saveOrder(long productId, LocalDateTime bucketTime, long salesAmount) {
        jdbcTemplate.update(
                "INSERT INTO product_order_metrics (product_id, bucket_time, order_count, quantity, sales_amount) " +
                        "VALUES (?, ?, 1, 1, ?)",
                productId, Timestamp.valueOf(bucketTime), salesAmount);
    }

    private List<Integer> rankPositions(String mvTable, String group) {
        return jdbcTemplate.queryForList(
                "SELECT rank_position FROM " + mvTable +
                        " WHERE anchor_date = ? AND weight_group = ? ORDER BY rank_position",
                Integer.class, java.sql.Date.valueOf(ANCHOR), group);
    }

    private double scoreOfMv(String mvTable, String anchorKey, String group, long productId) {
        Double s = jdbcTemplate.queryForObject(
                "SELECT score FROM " + mvTable +
                        " WHERE anchor_date = ? AND weight_group = ? AND product_id = ?",
                Double.class, java.sql.Date.valueOf(LocalDate.parse(
                        anchorKey.substring(0, 4) + "-" + anchorKey.substring(4, 6) + "-" + anchorKey.substring(6))),
                group, productId);
        return s == null ? 0.0 : s;
    }

    private JobParameters paramsOf(String anchorDate) {
        return new JobParametersBuilder()
                .addString(RankingJobParametersListener.PARAM_ANCHOR_DATE, anchorDate)
                .addLong("runTimestamp", System.nanoTime())
                .toJobParameters();
    }
}
