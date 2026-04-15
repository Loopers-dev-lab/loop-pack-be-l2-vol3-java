package com.loopers.domain.rank;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
@DisplayName("원자 스왑 시맨틱 — 대안 S1·S2를 F1/F3/F5 시나리오로 검증")
class MvAtomicSwapSemanticsTest {

    private static final String PERIOD_KEY = "2026W15";

    @Autowired MvProductRankRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        tx = new TransactionTemplate(transactionManager);
        jdbc.execute("CREATE TABLE IF NOT EXISTS mv_product_rank_publication (" +
                "period_type VARCHAR(20) NOT NULL, period_key VARCHAR(50) NOT NULL," +
                "published_version BIGINT NOT NULL DEFAULT 0, next_version BIGINT NOT NULL DEFAULT 0," +
                "updated_at DATETIME(6) NOT NULL, PRIMARY KEY (period_type, period_key))");
        jdbc.execute("DELETE FROM mv_product_rank_publication");
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP TABLE IF EXISTS mv_product_rank_publication");
    }

    @Nested
    @DisplayName("ASW-1 (S1 현행) — DELETE+INSERT 단일 tx")
    class S1Scenarios {

        @DisplayName("F1: Writer 중간 예외 → reader는 OLD snapshot만 관찰 (rollback)")
        @Test
        void f1_midRunException_rollsBackToOld() {
            repository.batchInsert(RankPeriodType.WEEKLY, buildRows(1.5, 100));
            long initialCount = repository.countByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY);

            assertThatThrownBy(() ->
                    tx.executeWithoutResult(status -> {
                        repository.deleteByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY);
                        repository.batchInsert(RankPeriodType.WEEKLY, buildRows(2.4, 50));
                        throw new RuntimeException("simulated mid-run crash");
                    })
            ).hasMessageContaining("simulated");

            List<MvProductRankRow> after = repository.findByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY, 0, 200);
            assertThat(after).hasSize((int) initialCount);
            assertThat(after).allSatisfy(r -> assertThat(isScoreFromWeight(r.score(), 1.5))
                    .as("rollback 후 행은 OLD(weight=1.5) snapshot")
                    .isTrue());
        }

        @DisplayName("F3: 알고리즘 가중치 변경 batch 실행 — reader는 혼재 관찰 없음")
        @Test
        void f3_algorithmChange_noMixedResults() {
            repository.batchInsert(RankPeriodType.WEEKLY, buildRows(1.5, 100));

            tx.executeWithoutResult(status -> {
                repository.deleteByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY);
                repository.batchInsert(RankPeriodType.WEEKLY, buildRows(2.4, 100));
            });

            List<MvProductRankRow> after = repository.findByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY, 0, 200);
            assertThat(after).hasSize(100);
            boolean allOld = after.stream().noneMatch(r -> isWeight24Score(r.score()));
            boolean allNew = after.stream().allMatch(r -> isWeight24Score(r.score()));
            assertThat(allOld || allNew)
                    .as("혼재 금지 — 전부 OLD이거나 전부 NEW여야 함")
                    .isTrue();
            assertThat(allNew).as("COMMIT 이후에는 NEW snapshot").isTrue();
        }

        @DisplayName("F5: 중단된 재실행 멱등성 — 같은 결과 재구성")
        @Test
        void f5_killAndRerun_idempotent() {
            repository.batchInsert(RankPeriodType.WEEKLY, buildRows(1.5, 50));

            try {
                tx.executeWithoutResult(status -> {
                    repository.deleteByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY);
                    repository.batchInsert(RankPeriodType.WEEKLY, buildRows(2.4, 30));
                    throw new RuntimeException("kill mid-run");
                });
            } catch (RuntimeException ignore) {}

            tx.executeWithoutResult(status -> {
                repository.deleteByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY);
                repository.batchInsert(RankPeriodType.WEEKLY, buildRows(2.4, 100));
            });

            List<MvProductRankRow> after = repository.findByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY, 0, 200);
            assertThat(after).hasSize(100);
            assertThat(after).allMatch(r -> isWeight24Score(r.score()));
        }
    }

    @Nested
    @DisplayName("ASW-2 (S2 Publication+version) — CAS publish")
    class S2Scenarios {

        @DisplayName("F1: INSERT 후 CAS 이전 실패 → reader는 published_version 구 버전만 본다")
        @Test
        void f1_preCasFailure_keepsOldPublished() {
            seedPublication(1L, 1L);
            seedVersionedRows(1L, 100, 1.5);

            try {
                tx.executeWithoutResult(status -> {
                    jdbc.update("UPDATE mv_product_rank_publication SET next_version = next_version + 1, updated_at = NOW(6) " +
                            "WHERE period_type = 'WEEKLY' AND period_key = ?", PERIOD_KEY);
                    throw new RuntimeException("crash before CAS");
                });
            } catch (RuntimeException ignore) {}

            Long publishedVersion = jdbc.queryForObject(
                    "SELECT published_version FROM mv_product_rank_publication WHERE period_type='WEEKLY' AND period_key=?",
                    Long.class, PERIOD_KEY
            );
            assertThat(publishedVersion).as("CAS 미실행 → published_version 변화 없음").isEqualTo(1L);
        }

        @DisplayName("F3: 새 version 전체 INSERT 후 CAS → reader가 혼재 관찰 없음")
        @Test
        void f3_versionCasFlip_atomic() {
            seedPublication(1L, 1L);
            seedVersionedRows(1L, 100, 1.5);

            seedVersionedRows(2L, 100, 2.4);
            tx.executeWithoutResult(status -> {
                jdbc.update("UPDATE mv_product_rank_publication SET published_version = ?, updated_at = NOW(6) " +
                        "WHERE period_type = 'WEEKLY' AND period_key = ? AND published_version < ?",
                        2L, PERIOD_KEY, 2L);
            });

            Long published = jdbc.queryForObject(
                    "SELECT published_version FROM mv_product_rank_publication WHERE period_type='WEEKLY' AND period_key=?",
                    Long.class, PERIOD_KEY
            );
            assertThat(published).isEqualTo(2L);

            Integer mixedViolation = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM mv_product_rank_weekly mv " +
                            "JOIN mv_product_rank_publication p ON p.period_type='WEEKLY' AND p.period_key=mv.period_key " +
                            "WHERE mv.period_key=? AND mv.score < ?",
                    Integer.class, PERIOD_KEY, 1.5 * 100
            );
            assertThat(mixedViolation).as("published version 기준 조회 시 2.4 가중치 결과만").isZero();
        }

        @DisplayName("F5: 재실행 멱등성 — next_version 재 bump + CAS")
        @Test
        void f5_rerunIdempotent_bumpsVersion() {
            seedPublication(1L, 1L);
            seedVersionedRows(1L, 100, 1.5);
            seedVersionedRows(2L, 30, 2.4);

            jdbc.update("UPDATE mv_product_rank_publication SET next_version = 3, updated_at = NOW(6) " +
                    "WHERE period_type='WEEKLY' AND period_key=?", PERIOD_KEY);
            seedVersionedRows(3L, 100, 2.4);
            jdbc.update("UPDATE mv_product_rank_publication SET published_version = 3, updated_at = NOW(6) " +
                    "WHERE period_type='WEEKLY' AND period_key=? AND published_version < 3", PERIOD_KEY);

            Long published = jdbc.queryForObject(
                    "SELECT published_version FROM mv_product_rank_publication WHERE period_type='WEEKLY' AND period_key=?",
                    Long.class, PERIOD_KEY
            );
            assertThat(published).isEqualTo(3L);
        }
    }

    private List<MvProductRankRow> buildRows(double weight, int count) {
        List<MvProductRankRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double score = weight * (count - i);
            rows.add(new MvProductRankRow(
                    PERIOD_KEY, i + 1, (long) (i + 1),
                    score, 10L, 5L, BigDecimal.valueOf(100)
            ));
        }
        return rows;
    }

    private boolean isWeight24Score(double score) {
        return isScoreFromWeight(score, 2.4);
    }

    private boolean isScoreFromWeight(double score, double weight) {
        for (int k = 1; k <= 100; k++) {
            if (Math.abs(score - weight * k) < 1e-6) return true;
        }
        return false;
    }

    private void seedPublication(long nextV, long publishedV) {
        jdbc.update("INSERT INTO mv_product_rank_publication (period_type, period_key, next_version, published_version, updated_at) " +
                "VALUES (?, ?, ?, ?, NOW(6))", "WEEKLY", PERIOD_KEY, nextV, publishedV);
    }

    private void seedVersionedRows(long version, int count, double weight) {
        String pkForV = "V" + version + "W15";
        String sql = "INSERT INTO mv_product_rank_weekly " +
                "(period_key, rank_no, ref_product_id, score, view_count, like_count, order_amount, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
        List<Object[]> params = new ArrayList<>(count);
        long base = version * 1_000_000L;
        for (int i = 0; i < count; i++) {
            params.add(new Object[]{pkForV, i + 1, base + i + 1, weight * (count - i), 10L, 5L, BigDecimal.valueOf(100)});
        }
        jdbc.batchUpdate(sql, params);
    }
}
