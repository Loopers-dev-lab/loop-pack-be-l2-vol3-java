package com.loopers.batch.job.ranking.fixture;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 시드 생성기 — Zipf α=1.2 분포로 product 별 일일 이벤트를 생성하고
 * product_view_metrics / product_like_metrics / product_order_metrics 에 적재한다.
 *
 * <p>설계.md "트래픽 전제 — 시드 규모와 상품 분포" 의 5-tier 비율을 따른다:
 * Hot 0.1% / Warm 1% / Normal 9% / Cold 20% / Sleeping 70%.
 * 활동 비율 (Hot+Warm+Normal+Cold) = 30%.</p>
 *
 * <p>이벤트 양 = floor(C / (rank+1)^1.2). C 는 Hot tier 의 일일 이벤트가 ~2000 이 되도록 보정.
 * 시간대는 단순화하여 일중 균등 분포 (5분 bucket 288개 중 무작위).</p>
 *
 * <p>view : like : order = 10 : 1 : 0.1 비율로 동일 product 에 시드.</p>
 */
public class BaselineSeeder {

    private static final int BUCKETS_PER_DAY = 24 * 12;             // 5분 bucket 288개
    private static final long BUCKET_SECONDS = 300L;
    private static final double ZIPF_ALPHA = 1.2;
    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;

    public BaselineSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 시드 실행 → 적재된 view row 수 반환. */
    public SeedReport seed(SeedSpec spec) {
        Random rng = new Random(spec.seed());
        LocalDate startDate = spec.anchorDate().minusDays(spec.historyDays() - 1L);

        // 활동 상품 = totalProducts × 30%, Sleeping 70% 는 시드 안 함
        int activeCount = (int) Math.round(spec.totalProducts() * 0.30);

        // Zipf 정규화 상수 — Hot tier 첫 product 의 일일 이벤트가 약 2000 이 되도록 보정
        double scaleC = 2000.0;

        List<long[]> viewRows = new ArrayList<>();   // (productId, bucketEpochSec, count)
        List<long[]> likeRows = new ArrayList<>();
        List<long[]> orderRows = new ArrayList<>();

        for (int rank = 0; rank < activeCount; rank++) {
            long productId = rank + 1L;
            int dailyViews = Math.max(1, (int) (scaleC / Math.pow(rank + 1, ZIPF_ALPHA)));
            int dailyLikes = Math.max(0, dailyViews / 10);
            int dailyOrders = Math.max(0, dailyViews / 100);

            for (int day = 0; day < spec.historyDays(); day++) {
                LocalDate date = startDate.plusDays(day);
                accumulate(viewRows,  productId, date, dailyViews,  rng);
                accumulate(likeRows,  productId, date, dailyLikes,  rng);
                accumulate(orderRows, productId, date, dailyOrders, rng);
            }
        }

        int viewInserted  = bulkInsertViews(viewRows);
        int likeInserted  = bulkInsertLikes(likeRows);
        int orderInserted = bulkInsertOrders(orderRows);

        return new SeedReport(
                spec.totalProducts(), activeCount,
                viewInserted, likeInserted, orderInserted);
    }

    /** 한 product 의 하루치 이벤트를 5분 bucket 에 무작위 분산. */
    private void accumulate(List<long[]> rows, long productId, LocalDate date, int dailyTotal, Random rng) {
        if (dailyTotal <= 0) return;

        int[] bucketCounts = new int[BUCKETS_PER_DAY];
        for (int i = 0; i < dailyTotal; i++) {
            bucketCounts[rng.nextInt(BUCKETS_PER_DAY)]++;
        }

        long midnightSec = date.atStartOfDay().toEpochSecond(java.time.ZoneOffset.UTC);
        for (int b = 0; b < BUCKETS_PER_DAY; b++) {
            if (bucketCounts[b] == 0) continue;
            rows.add(new long[]{productId, midnightSec + (long) b * BUCKET_SECONDS, bucketCounts[b]});
        }
    }

    private int bulkInsertViews(List<long[]> rows) {
        return bulkInsert(
                "INSERT INTO product_view_metrics (product_id, bucket_time, view_count) VALUES (?, ?, ?)",
                rows);
    }

    private int bulkInsertLikes(List<long[]> rows) {
        return bulkInsert(
                "INSERT INTO product_like_metrics (product_id, bucket_time, like_count) VALUES (?, ?, ?)",
                rows);
    }

    private int bulkInsertOrders(List<long[]> rows) {
        // sales_amount = count × 10000 (가상의 단가).
        // rewriteBatchedStatements=true 가 활성된 MySQL 드라이버는 batchUpdate 결과로
        // SUCCESS_NO_INFO(-2) 를 반환하므로 row 수를 chunk.size() 로 누적한다.
        String sql = "INSERT INTO product_order_metrics " +
                "(product_id, bucket_time, order_count, quantity, sales_amount) " +
                "VALUES (?, ?, ?, ?, ?)";
        int total = 0;
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, rows.size());
            List<long[]> chunk = rows.subList(start, end);
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                    long[] r = chunk.get(i);
                    ps.setLong(1, r[0]);
                    ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.ofEpochSecond(r[1], 0, java.time.ZoneOffset.UTC)));
                    ps.setInt(3, (int) r[2]);
                    ps.setLong(4, r[2]);
                    ps.setLong(5, r[2] * 10_000L);
                }
                @Override public int getBatchSize() { return chunk.size(); }
            });
            total += chunk.size();
        }
        return total;
    }

    private int bulkInsert(String sql, List<long[]> rows) {
        int total = 0;
        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, rows.size());
            List<long[]> chunk = rows.subList(start, end);
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override public void setValues(PreparedStatement ps, int i) throws SQLException {
                    long[] r = chunk.get(i);
                    ps.setLong(1, r[0]);
                    ps.setTimestamp(2, Timestamp.valueOf(LocalDateTime.ofEpochSecond(r[1], 0, java.time.ZoneOffset.UTC)));
                    ps.setLong(3, r[2]);
                }
                @Override public int getBatchSize() { return chunk.size(); }
            });
            total += chunk.size();
        }
        return total;
    }

    public record SeedReport(int totalProducts, int activeProducts,
                             int viewRowsInserted, int likeRowsInserted, int orderRowsInserted) {
    }
}
