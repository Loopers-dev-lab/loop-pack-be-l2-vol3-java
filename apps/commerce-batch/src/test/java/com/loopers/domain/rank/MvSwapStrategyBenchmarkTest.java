package com.loopers.domain.rank;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
@DisplayName("S1 vs S2 스왑 전략 벤치마크 — 순수 SWAP 단계 비용 비교")
class MvSwapStrategyBenchmarkTest {

    private static final String PERIOD_KEY = "2026W15";
    private static final int ROW_COUNT = 10_000;

    @Autowired
    private MvProductRankRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("S1 SWAP 단계: DELETE by period_key(10K rows) — tx 지속 시간 실측")
    @Test
    void s1_swap_delete_phase_duration() {
        repository.batchInsert(RankPeriodType.WEEKLY, buildRows(ROW_COUNT));

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        long insertStart = System.nanoTime();
        long[] phase = new long[2];
        tx.executeWithoutResult(status -> {
            long deleteStart = System.nanoTime();
            repository.deleteByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY);
            phase[0] = (System.nanoTime() - deleteStart) / 1_000_000L;
            long insertPhaseStart = System.nanoTime();
            repository.batchInsert(RankPeriodType.WEEKLY, buildRows(ROW_COUNT));
            phase[1] = (System.nanoTime() - insertPhaseStart) / 1_000_000L;
        });
        long totalMs = (System.nanoTime() - insertStart) / 1_000_000L;

        System.out.printf("[S1-BENCHMARK] rows=%d delete_ms=%d insert_ms=%d total_tx_ms=%d%n",
                ROW_COUNT, phase[0], phase[1], totalMs);
    }

    @DisplayName("S2 SWAP 단계: bump + INSERT(with version) + CAS publish UPDATE — tx 지속 시간 실측")
    @Test
    void s2_swap_publication_phase_duration() {
        long seedVersion = 1L;
        jdbcTemplate.update(
                "INSERT INTO mv_product_rank_publication (period_type, period_key, next_version, published_version, updated_at) " +
                        "VALUES (?, ?, ?, ?, NOW(6))",
                "WEEKLY", PERIOD_KEY, seedVersion, seedVersion
        );
        seedS2Rows(seedVersion, ROW_COUNT);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        long[] phase = new long[3];
        long txStart = System.nanoTime();
        tx.executeWithoutResult(status -> {
            long bumpStart = System.nanoTime();
            jdbcTemplate.update(
                    "UPDATE mv_product_rank_publication SET next_version = next_version + 1, updated_at = NOW(6) " +
                            "WHERE period_type = ? AND period_key = ?",
                    "WEEKLY", PERIOD_KEY
            );
            Long newVersion = jdbcTemplate.queryForObject(
                    "SELECT next_version FROM mv_product_rank_publication WHERE period_type = ? AND period_key = ?",
                    Long.class, "WEEKLY", PERIOD_KEY
            );
            phase[0] = (System.nanoTime() - bumpStart) / 1_000_000L;

            long insertStart = System.nanoTime();
            seedS2Rows(newVersion == null ? 2L : newVersion, ROW_COUNT);
            phase[1] = (System.nanoTime() - insertStart) / 1_000_000L;

            long casStart = System.nanoTime();
            jdbcTemplate.update(
                    "UPDATE mv_product_rank_publication SET published_version = ?, updated_at = NOW(6) " +
                            "WHERE period_type = ? AND period_key = ? AND published_version < ?",
                    newVersion, "WEEKLY", PERIOD_KEY, newVersion
            );
            phase[2] = (System.nanoTime() - casStart) / 1_000_000L;
        });
        long totalMs = (System.nanoTime() - txStart) / 1_000_000L;

        System.out.printf("[S2-BENCHMARK] rows=%d bump_ms=%d insert_ms=%d cas_ms=%d total_tx_ms=%d%n",
                ROW_COUNT, phase[0], phase[1], phase[2], totalMs);
    }

    @DisplayName("S1-only SWAP 최소 비용: DELETE by period_key (INSERT 제외)")
    @Test
    void s1_delete_only_cost() {
        repository.batchInsert(RankPeriodType.WEEKLY, buildRows(ROW_COUNT));

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        long[] elapsed = new long[1];
        tx.executeWithoutResult(status -> {
            long start = System.nanoTime();
            repository.deleteByPeriodKey(RankPeriodType.WEEKLY, PERIOD_KEY);
            elapsed[0] = (System.nanoTime() - start) / 1_000_000L;
        });
        System.out.printf("[S1-DELETE-ONLY] rows=%d delete_tx_ms=%d%n", ROW_COUNT, elapsed[0]);
    }

    @DisplayName("S2-only SWAP 최소 비용: 단일 CAS UPDATE (INSERT 제외)")
    @Test
    void s2_cas_only_cost() {
        long seedVersion = 1L;
        jdbcTemplate.update(
                "INSERT INTO mv_product_rank_publication (period_type, period_key, next_version, published_version, updated_at) " +
                        "VALUES (?, ?, ?, ?, NOW(6))",
                "WEEKLY", PERIOD_KEY, 2L, seedVersion
        );

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        long[] elapsed = new long[1];
        tx.executeWithoutResult(status -> {
            long start = System.nanoTime();
            jdbcTemplate.update(
                    "UPDATE mv_product_rank_publication SET published_version = ?, updated_at = NOW(6) " +
                            "WHERE period_type = ? AND period_key = ? AND published_version < ?",
                    2L, "WEEKLY", PERIOD_KEY, 2L
            );
            elapsed[0] = (System.nanoTime() - start) / 1_000_000L;
        });
        System.out.printf("[S2-CAS-ONLY] cas_tx_ms=%d%n", elapsed[0]);
    }

    private void seedS2Rows(long version, int count) {
        String sql = "INSERT INTO mv_product_rank_weekly " +
                "(period_key, version, rank_no, ref_product_id, score, view_count, like_count, order_amount, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        Timestamp now = Timestamp.from(Instant.now());
        List<Object[]> params = new ArrayList<>(count);
        long rankOffset = (version - 1) * 1_000_000L;
        for (int i = 0; i < count; i++) {
            params.add(new Object[]{
                    PERIOD_KEY,
                    version,
                    i + 1,
                    rankOffset + i + 1,
                    (double) (count - i),
                    10L, 5L, BigDecimal.valueOf(100),
                    now, now
            });
        }
        jdbcTemplate.batchUpdate(sql, params);
    }

    private List<MvProductRankRow> buildRows(int count) {
        List<MvProductRankRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new MvProductRankRow(
                    PERIOD_KEY,
                    i + 1,
                    (long) (i + 1),
                    (double) (count - i),
                    10L, 5L, BigDecimal.valueOf(100)
            ));
        }
        return rows;
    }
}
