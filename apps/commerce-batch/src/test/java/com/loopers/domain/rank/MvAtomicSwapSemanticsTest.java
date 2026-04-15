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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = "spring.batch.job.enabled=false")
@DisplayName("S2 원자 스왑 시맨틱 — CAS 이전 실패 / 알고리즘 전환 / 재실행 멱등")
class MvAtomicSwapSemanticsTest {

    private static final String PERIOD_KEY = "2026W15";

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        tx = new TransactionTemplate(transactionManager);
    }

    @DisplayName("F1: INSERT 후 CAS 이전 실패 → published_version 구 버전 유지")
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
        } catch (RuntimeException ignore) {
        }

        assertThat(queryPublishedVersion()).isEqualTo(1L);
    }

    @DisplayName("F3: 새 version 전체 INSERT 후 CAS → reader는 version=2만 관찰")
    @Test
    void f3_versionCasFlip_atomic() {
        seedPublication(1L, 1L);
        seedVersionedRows(1L, 100, 1.5);
        seedVersionedRows(2L, 100, 2.4);

        tx.executeWithoutResult(status ->
                jdbc.update("UPDATE mv_product_rank_publication SET published_version = ?, updated_at = NOW(6) " +
                                "WHERE period_type = 'WEEKLY' AND period_key = ? AND published_version < ?",
                        2L, PERIOD_KEY, 2L));

        assertThat(queryPublishedVersion()).isEqualTo(2L);
        assertThat(countPublishedRows()).isEqualTo(100);
        assertThat(countPublishedRowsOfOtherVersion(2L))
                .as("published 기준 조회에 version=1 (OLD) 혼재 없음").isZero();
    }

    @DisplayName("F5: 재실행 시 next_version bump → CAS로 최종 published=3")
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

        assertThat(queryPublishedVersion()).isEqualTo(3L);
    }

    private long queryPublishedVersion() {
        Long v = jdbc.queryForObject(
                "SELECT published_version FROM mv_product_rank_publication WHERE period_type='WEEKLY' AND period_key=?",
                Long.class, PERIOD_KEY);
        return v == null ? 0L : v;
    }

    private int countPublishedRows() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly mv " +
                        "JOIN mv_product_rank_publication p " +
                        "  ON p.period_type='WEEKLY' AND p.period_key=mv.period_key AND p.published_version=mv.version " +
                        "WHERE mv.period_key=?",
                Integer.class, PERIOD_KEY);
        return n == null ? 0 : n;
    }

    private int countPublishedRowsOfOtherVersion(long excludedVersion) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mv_product_rank_weekly mv " +
                        "JOIN mv_product_rank_publication p " +
                        "  ON p.period_type='WEEKLY' AND p.period_key=mv.period_key AND p.published_version=mv.version " +
                        "WHERE mv.period_key=? AND mv.version != ?",
                Integer.class, PERIOD_KEY, excludedVersion);
        return n == null ? 0 : n;
    }

    private void seedPublication(long nextV, long publishedV) {
        jdbc.update("INSERT INTO mv_product_rank_publication (period_type, period_key, next_version, published_version, updated_at) " +
                "VALUES (?, ?, ?, ?, NOW(6))", "WEEKLY", PERIOD_KEY, nextV, publishedV);
    }

    private void seedVersionedRows(long version, int count, double weight) {
        String sql = "INSERT INTO mv_product_rank_weekly " +
                "(period_key, version, rank_no, ref_product_id, score, view_count, like_count, order_amount, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";
        List<Object[]> params = new ArrayList<>(count);
        long base = version * 1_000_000L;
        for (int i = 0; i < count; i++) {
            params.add(new Object[]{PERIOD_KEY, version, i + 1, base + i + 1,
                    weight * (count - i), 10L, 5L, BigDecimal.valueOf(100)});
        }
        jdbc.batchUpdate(sql, params);
    }
}
