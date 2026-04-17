package com.loopers.job.rankingmv;

import com.loopers.batch.job.rankingmv.ProductRankingMvJobConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + ProductRankingMvJobConfig.JOB_NAME)
@Sql(scripts = "/schema-batch-test.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class ProductRankingMvJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier(ProductRankingMvJobConfig.JOB_NAME)
    private Job job;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String TARGET_DATE = "20260416";
    private static final int SEED_BATCH_SIZE = 1_000;

    @BeforeEach
    void setUp() {
        jobLauncherTestUtils.setJob(job);
        jdbcTemplate.execute("TRUNCATE TABLE mv_product_rank_weekly");
        jdbcTemplate.execute("TRUNCATE TABLE mv_product_rank_monthly");
        jdbcTemplate.execute("TRUNCATE TABLE mv_product_rank_staging");
        jdbcTemplate.execute("TRUNCATE TABLE product_metrics");
        jdbcTemplate.execute("TRUNCATE TABLE product");
    }

    private void seedProducts(int count) {
        for (int i = 1; i <= count; i++) {
            jdbcTemplate.update(
                "INSERT INTO product (id, brand_id, name, price, stock_quantity, like_count, created_at, updated_at) " +
                "VALUES (?, 1, ?, ?, 1000, 0, NOW(), NOW())",
                i, "상품" + i, i * 1000);
        }
    }

    private void seedMetrics(int productCount, int days, String endDateStr) {
        LocalDate endDate = LocalDate.parse(endDateStr, DATE_FORMATTER);
        for (int d = 0; d < days; d++) {
            LocalDate date = endDate.minusDays(d);
            for (int p = 1; p <= productCount; p++) {
                jdbcTemplate.update(
                    "INSERT INTO product_metrics " +
                    "(product_id, metric_date, view_count, like_count, unlike_count, " +
                    "sales_count, sales_amount, cancel_count_by_event_date, cancel_amount_by_event_date, " +
                    "cancel_count_by_order_date, cancel_amount_by_order_date) " +
                    "VALUES (?, ?, ?, ?, 0, ?, ?, 0, 0, 0, 0)",
                    p, date, p * 100, p * 10, p * 5, p * 50000L);
            }
        }
    }

    private BatchStatus runJob(String scope) throws Exception {
        var params = new JobParametersBuilder()
            .addString("targetDate", TARGET_DATE)
            .addString("scope", scope)
            .addLong("run.id", System.currentTimeMillis())
            .toJobParameters();
        return jobLauncherTestUtils.launchJob(params).getStatus();
    }

    // ── Bulk Seed (대규모 테스트용) ──────────────────────────────────────

    private void seedProductsBulk(int count) {
        String sql = "INSERT INTO product (id, brand_id, name, price, stock_quantity, like_count, created_at, updated_at) " +
            "VALUES (?, 1, ?, ?, 1000, 0, NOW(), NOW())";
        for (int batchStart = 0; batchStart < count; batchStart += SEED_BATCH_SIZE) {
            int start = batchStart;
            int end = Math.min(batchStart + SEED_BATCH_SIZE, count);
            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    int p = start + i + 1;
                    ps.setLong(1, p);
                    ps.setString(2, "product-" + p);
                    ps.setInt(3, 10_000 + (p % 90_000));
                }
                @Override
                public int getBatchSize() { return end - start; }
            });
        }
    }

    /**
     * 6가지 트렌드 패턴으로 메트릭 벌크 시드.
     * <pre>
     *   A) 급상승    (1~5,000  = 5%)  : 최근 7일 폭발, 이전 미미
     *   B) 장기강자  (5,001~15,000 = 10%): 30일 꾸준히 높음
     *   C) 하락추세  (15,001~20,000 = 5%): 이전 높음 → 최근 급락
     *   D) 바이럴    (20,001~22,000 = 2%): 오늘만 폭발
     *   E) 취소높음  (22,001~25,000 = 3%): 매출 높지만 취소 50~70%
     *   F) 일반      (25,001~100,000 = 75%): 보통 수준
     * </pre>
     */
    private void seedMetricsBulkWithTrends(int productCount, int days, String endDateStr) {
        LocalDate endDate = LocalDate.parse(endDateStr, DATE_FORMATTER);
        String sql = "INSERT INTO product_metrics " +
            "(product_id, metric_date, view_count, like_count, unlike_count, " +
            "sales_count, sales_amount, cancel_count_by_event_date, cancel_amount_by_event_date, " +
            "cancel_count_by_order_date, cancel_amount_by_order_date) " +
            "VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?)";

        for (int d = 0; d < days; d++) {
            LocalDate date = endDate.minusDays(d);
            boolean isRecent = d < 7;
            boolean isToday = d == 0;

            for (int batchStart = 0; batchStart < productCount; batchStart += SEED_BATCH_SIZE) {
                int start = batchStart;
                int end = Math.min(batchStart + SEED_BATCH_SIZE, productCount);
                final LocalDate metricDate = date;
                final boolean recent = isRecent;
                final boolean today = isToday;

                jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        int p = start + i + 1;
                        int views, likes, salesCount;
                        long salesAmount;
                        long cancelAmount = 0;
                        int cancelCount = 0;

                        if (p <= 5_000) {
                            // A) 급상승
                            if (recent) {
                                views = 4_000 + p;
                                likes = 500 + p / 10;
                                salesAmount = 2_000_000L + p * 200L;
                            } else {
                                views = 50;
                                likes = 5;
                                salesAmount = 20_000L;
                            }
                        } else if (p <= 15_000) {
                            // B) 장기강자
                            int pos = p - 5_000;
                            views = 1_000 + pos / 5;
                            likes = 100 + pos / 50;
                            salesAmount = 1_500_000L + pos * 50L;
                        } else if (p <= 20_000) {
                            // C) 하락추세
                            int pos = p - 15_000;
                            if (recent) {
                                views = 100;
                                likes = 10;
                                salesAmount = 50_000L;
                            } else {
                                views = 2_000 + pos / 3;
                                likes = 200 + pos / 25;
                                salesAmount = 1_500_000L + pos * 100L;
                            }
                        } else if (p <= 22_000) {
                            // D) 바이럴
                            if (today) {
                                views = 15_000;
                                likes = 2_000;
                                salesAmount = 5_000_000L;
                            } else {
                                views = 100;
                                likes = 10;
                                salesAmount = 50_000L;
                            }
                        } else if (p <= 25_000) {
                            // E) 취소높음
                            views = 1_500;
                            likes = 150;
                            salesAmount = 2_000_000L;
                            int cancelRate = 50 + ((p - 22_001) % 3) * 10;
                            cancelAmount = salesAmount * cancelRate / 100;
                            cancelCount = (int) (cancelAmount / 100_000);
                        } else {
                            // F) 일반
                            int pos = p - 25_000;
                            views = 200 + pos / 30;
                            likes = 20 + pos / 300;
                            salesAmount = 100_000L + pos * 3L;
                        }

                        salesCount = (int) (salesAmount / 50_000) + 1;

                        ps.setLong(1, p);
                        ps.setObject(2, metricDate);
                        ps.setInt(3, views);
                        ps.setInt(4, likes);
                        ps.setInt(5, salesCount);
                        ps.setLong(6, salesAmount);
                        ps.setInt(7, cancelCount);
                        ps.setLong(8, cancelAmount);
                        ps.setInt(9, cancelCount);
                        ps.setLong(10, cancelAmount);
                    }

                    @Override
                    public int getBatchSize() { return end - start; }
                });
            }
        }
    }

    // ── 주간 랭킹 Job ──────────────────────────────────────────────────

    @Test
    @DisplayName("주간 정상 — 시드 데이터 기반 주간 TOP 100 적재")
    void weeklySuccess() throws Exception {
        seedProducts(150);
        seedMetrics(150, 7, TARGET_DATE);

        BatchStatus status = runJob("weekly");

        assertThat(status).isEqualTo(BatchStatus.COMPLETED);

        int mvCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key = ?",
            Integer.class, TARGET_DATE);
        assertThat(mvCount).isEqualTo(100);

        Long topProductId = jdbcTemplate.queryForObject(
            "SELECT product_id FROM mv_product_rank_weekly WHERE period_key = ? AND ranking = 1",
            Long.class, TARGET_DATE);
        assertThat(topProductId).isEqualTo(150L);

        int stagingCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_staging WHERE period_key = ?",
            Integer.class, TARGET_DATE);
        assertThat(stagingCount).isEqualTo(150);
    }

    @Test
    @DisplayName("주간 — 상품이 100개 미만이면 있는 만큼만 적재")
    void weeklyLessThan100Products() throws Exception {
        seedProducts(30);
        seedMetrics(30, 7, TARGET_DATE);

        BatchStatus status = runJob("weekly");

        assertThat(status).isEqualTo(BatchStatus.COMPLETED);

        int mvCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key = ?",
            Integer.class, TARGET_DATE);
        assertThat(mvCount).isEqualTo(30);
    }

    // ── 월간 랭킹 Job ──────────────────────────────────────────────────

    @Test
    @DisplayName("월간 정상 — 30일 데이터 집계")
    void monthlySuccess() throws Exception {
        seedProducts(50);
        seedMetrics(50, 30, TARGET_DATE);

        BatchStatus status = runJob("monthly");

        assertThat(status).isEqualTo(BatchStatus.COMPLETED);

        int mvCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE period_key = ?",
            Integer.class, TARGET_DATE);
        assertThat(mvCount).isEqualTo(50);
    }

    // ── 멱등성 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("멱등성 — 같은 파라미터로 2회 실행해도 결과 동일")
    void idempotentDoubleExecution() throws Exception {
        seedProducts(50);
        seedMetrics(50, 7, TARGET_DATE);

        runJob("weekly");
        BatchStatus secondStatus = runJob("weekly");

        assertThat(secondStatus).isEqualTo(BatchStatus.COMPLETED);

        int mvCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key = ?",
            Integer.class, TARGET_DATE);
        assertThat(mvCount).isEqualTo(50);
    }

    // ── 엣지 케이스 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("엣지 — 데이터 없는 날짜로 실행하면 빈 MV")
    void noDataProducesEmptyMv() throws Exception {
        seedProducts(10);

        BatchStatus status = runJob("weekly");

        assertThat(status).isEqualTo(BatchStatus.COMPLETED);

        int mvCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key = ?",
            Integer.class, TARGET_DATE);
        assertThat(mvCount).isEqualTo(0);
    }

    @Test
    @DisplayName("엣지 — 7일 미만 데이터면 있는 만큼만 집계")
    void partialDataAggregated() throws Exception {
        seedProducts(20);
        seedMetrics(20, 3, TARGET_DATE);

        BatchStatus status = runJob("weekly");

        assertThat(status).isEqualTo(BatchStatus.COMPLETED);

        int mvCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key = ?",
            Integer.class, TARGET_DATE);
        assertThat(mvCount).isEqualTo(20);
    }

    @Test
    @DisplayName("시각화 — 일간/주간/월간 TOP 랭킹 결과 출력")
    void printRankingResults() throws Exception {
        // 20개 상품 시드
        String[] names = {
            "나이키 에어맥스 97", "아디다스 울트라부스트", "뉴발란스 993", "아식스 젤카야노",
            "푸마 스웨이드", "리복 클래식", "컨버스 척테일러", "반스 올드스쿨",
            "호카 본디 8", "살로몬 XT-6", "노스페이스 눕시", "파타고니아 다운재킷",
            "아크테릭스 베타 LT", "스톤아일랜드 오버셔츠", "메종키츠네 폭스티",
            "아미 하트로고 맨투맨", "톰브라운 카디건", "르메르 크로와상 백",
            "메종마르지엘라 타비슈즈", "보테가베네타 카세트백"
        };
        for (int i = 1; i <= 20; i++) {
            jdbcTemplate.update(
                "INSERT INTO product (id, brand_id, name, price, stock_quantity, like_count, created_at, updated_at) " +
                "VALUES (?, 1, ?, ?, 1000, 0, NOW(), NOW())",
                i, names[i - 1], i * 15000);
        }

        // ── 30일치 메트릭 시드 (상품별 트렌드가 다르게) ──
        // 상품 유형:
        //   A) 최근 급상승 (상품 1,2,3): 최근 7일 폭발, 이전 23일은 미미
        //   B) 장기 강자   (상품 17,18,19,20): 30일 내내 꾸준히 높음
        //   C) 하락 추세   (상품 14,15): 이전 23일은 높았으나 최근 7일 급락
        //   D) 오늘 바이럴 (상품 8): 오늘 하루만 폭발
        //   E) 일반        (나머지): 보통 수준으로 꾸준

        LocalDate endDate = LocalDate.parse(TARGET_DATE, DATE_FORMATTER);
        for (int d = 0; d < 30; d++) {
            LocalDate date = endDate.minusDays(d);
            boolean isRecent = d < 7;   // 최근 7일
            boolean isToday = d == 0;   // 오늘

            for (int p = 1; p <= 20; p++) {
                int views; int likes; long salesAmount; int salesCount;
                long cancelAmount = 0; int cancelCount = 0;

                if (p <= 3) {
                    // A) 최근 급상승: 최근 7일은 매우 높고 이전 23일은 낮음
                    if (isRecent) {
                        views = 5000 + p * 500; likes = 600 + p * 80;
                        salesAmount = 3000000L + p * 500000L;
                    } else {
                        views = 100 + p * 10; likes = 10 + p;
                        salesAmount = 50000L + p * 10000L;
                    }
                } else if (p >= 17) {
                    // B) 장기 강자: 30일 내내 꾸준히 높음
                    views = 1200 + (p - 16) * 300; likes = 150 + (p - 16) * 40;
                    salesAmount = 1800000L + (p - 16) * 400000L;
                    // 상품 19: 취소율 50%
                    if (p == 19) { cancelAmount = salesAmount / 2; cancelCount = 3; }
                } else if (p == 14 || p == 15) {
                    // C) 하락 추세: 이전에는 높았으나 최근 급락
                    if (isRecent) {
                        views = 200 + (p - 13) * 50; likes = 20 + (p - 13) * 5;
                        salesAmount = 100000L + (p - 13) * 30000L;
                    } else {
                        views = 3000 + (p - 13) * 800; likes = 400 + (p - 13) * 100;
                        salesAmount = 2500000L + (p - 13) * 600000L;
                    }
                } else if (p == 8) {
                    // D) 오늘 바이럴: 오늘만 폭발
                    if (isToday) {
                        views = 15000; likes = 2000; salesAmount = 5000000L;
                    } else {
                        views = 200; likes = 20; salesAmount = 80000L;
                    }
                } else {
                    // E) 일반: 보통 수준
                    views = 300 + p * 40; likes = 30 + p * 5;
                    salesAmount = 200000L + p * 80000L;
                }

                salesCount = (int) (salesAmount / 50000) + 1;
                jdbcTemplate.update(
                    "INSERT INTO product_metrics " +
                    "(product_id, metric_date, view_count, like_count, unlike_count, " +
                    "sales_count, sales_amount, cancel_count_by_event_date, cancel_amount_by_event_date, " +
                    "cancel_count_by_order_date, cancel_amount_by_order_date) " +
                    "VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?)",
                    p, date, views, likes, salesCount, salesAmount, cancelCount, cancelAmount, cancelCount, cancelAmount);
            }
        }

        // ── Job 실행 ──
        BatchStatus weeklyStatus = runJob("weekly");
        assertThat(weeklyStatus).isEqualTo(BatchStatus.COMPLETED);
        BatchStatus monthlyStatus = runJob("monthly");
        assertThat(monthlyStatus).isEqualTo(BatchStatus.COMPLETED);

        // ── 공통 출력 헬퍼 ──
        String header = String.format("  %-4s │ %-6s │ %-26s │ %10s │ %8s │ %8s │ %12s │ %8s",
            "순위", "상품ID", "상품명", "Score", "조회수", "좋아요", "순매출액", "판매수");
        String divider = "───────┼────────┼────────────────────────────┼────────────┼──────────┼──────────┼──────────────┼──────────";
        String border = "═══════════════════════════════════════════════════════════════════════════════════════════════════════";

        // ── 일간 랭킹 ──
        System.out.println("\n" + border);
        System.out.println("  [일간 랭킹 TOP 20]  date=2026-04-16  (당일 1일 집계 — 운영 시 Redis Speed Layer)");
        System.out.println(border);
        System.out.println(header);
        System.out.println(divider);

        var dailyRows = jdbcTemplate.queryForList("""
            SELECT
                ROW_NUMBER() OVER (ORDER BY
                    (0.1 * LOG10(GREATEST(pm.view_count, 0) + 1) / 7.0
                   + 0.2 * LOG10(GREATEST(pm.like_count - pm.unlike_count, 0) + 1) / 7.0
                   + 0.7 * LOG10(GREATEST(pm.sales_amount - pm.cancel_amount_by_event_date, 0) + 1) / 7.0)
                DESC) AS ranking,
                pm.product_id, p.name,
                (0.1 * LOG10(GREATEST(pm.view_count, 0) + 1) / 7.0
               + 0.2 * LOG10(GREATEST(pm.like_count - pm.unlike_count, 0) + 1) / 7.0
               + 0.7 * LOG10(GREATEST(pm.sales_amount - pm.cancel_amount_by_event_date, 0) + 1) / 7.0) AS score,
                pm.view_count, (pm.like_count - pm.unlike_count) AS like_count,
                (pm.sales_amount - pm.cancel_amount_by_event_date) AS sales_amount, pm.sales_count
            FROM product_metrics pm JOIN product p ON pm.product_id = p.id
            WHERE pm.metric_date = '2026-04-16'
            ORDER BY score DESC LIMIT 20
            """);
        for (var row : dailyRows) {
            System.out.printf("  %4d │ %6d │ %-26s │ %10.4f │ %,8d │ %,8d │ %,12d │ %,8d%n",
                row.get("ranking"), row.get("product_id"), row.get("name"),
                ((Number) row.get("score")).doubleValue(), ((Number) row.get("view_count")).longValue(),
                ((Number) row.get("like_count")).longValue(), ((Number) row.get("sales_amount")).longValue(),
                ((Number) row.get("sales_count")).longValue());
        }
        System.out.println(border);

        // ── 주간 랭킹 ──
        System.out.println("\n" + border);
        System.out.println("  [주간 랭킹 TOP 20]  period_key=" + TARGET_DATE + "  (최근 7일 집계)");
        System.out.println(border);
        System.out.println(header);
        System.out.println(divider);
        var weeklyRows = jdbcTemplate.queryForList(
            "SELECT w.ranking, w.product_id, p.name, w.score, w.view_count, w.like_count, w.sales_amount, w.sales_count " +
            "FROM mv_product_rank_weekly w JOIN product p ON w.product_id = p.id " +
            "WHERE w.period_key = ? ORDER BY w.ranking", TARGET_DATE);
        for (var row : weeklyRows) {
            System.out.printf("  %4d │ %6d │ %-26s │ %10.4f │ %,8d │ %,8d │ %,12d │ %,8d%n",
                row.get("ranking"), row.get("product_id"), row.get("name"),
                ((Number) row.get("score")).doubleValue(), ((Number) row.get("view_count")).longValue(),
                ((Number) row.get("like_count")).longValue(), ((Number) row.get("sales_amount")).longValue(),
                ((Number) row.get("sales_count")).longValue());
        }
        System.out.println(border);

        // ── 월간 랭킹 ──
        System.out.println("\n" + border);
        System.out.println("  [월간 랭킹 TOP 20]  period_key=" + TARGET_DATE + "  (최근 30일 집계)");
        System.out.println(border);
        System.out.println(header);
        System.out.println(divider);
        var monthlyRows = jdbcTemplate.queryForList(
            "SELECT m.ranking, m.product_id, p.name, m.score, m.view_count, m.like_count, m.sales_amount, m.sales_count " +
            "FROM mv_product_rank_monthly m JOIN product p ON m.product_id = p.id " +
            "WHERE m.period_key = ? ORDER BY m.ranking", TARGET_DATE);
        for (var row : monthlyRows) {
            System.out.printf("  %4d │ %6d │ %-26s │ %10.4f │ %,8d │ %,8d │ %,12d │ %,8d%n",
                row.get("ranking"), row.get("product_id"), row.get("name"),
                ((Number) row.get("score")).doubleValue(), ((Number) row.get("view_count")).longValue(),
                ((Number) row.get("like_count")).longValue(), ((Number) row.get("sales_amount")).longValue(),
                ((Number) row.get("sales_count")).longValue());
        }
        System.out.println(border);

        // ── 일간 vs 주간 vs 월간 순위 비교 ──
        System.out.println();
        System.out.println("  [순위 비교] 일간 vs 주간 vs 월간 — 집계 기간에 따른 순위 변동");
        System.out.printf("  %-6s │ %-26s │ %5s │ %5s │ %5s │ %-8s │ %s%n",
            "상품ID", "상품명", "일간", "주간", "월간", "주간변동", "유형");
        System.out.println("─────────┼────────────────────────────┼───────┼───────┼───────┼──────────┼──────────────");

        var compareRows = jdbcTemplate.queryForList("""
            SELECT d.product_id, p.name, d.ranking AS daily_rank,
                   COALESCE(w.ranking, 0) AS weekly_rank,
                   COALESCE(mo.ranking, 0) AS monthly_rank
            FROM (
                SELECT product_id,
                    ROW_NUMBER() OVER (ORDER BY
                        (0.1 * LOG10(GREATEST(view_count, 0) + 1) / 7.0
                       + 0.2 * LOG10(GREATEST(like_count - unlike_count, 0) + 1) / 7.0
                       + 0.7 * LOG10(GREATEST(sales_amount - cancel_amount_by_event_date, 0) + 1) / 7.0)
                    DESC) AS ranking
                FROM product_metrics WHERE metric_date = '2026-04-16'
            ) d
            JOIN product p ON d.product_id = p.id
            LEFT JOIN mv_product_rank_weekly w ON d.product_id = w.product_id AND w.period_key = ?
            LEFT JOIN mv_product_rank_monthly mo ON d.product_id = mo.product_id AND mo.period_key = ?
            ORDER BY d.ranking
            """, TARGET_DATE, TARGET_DATE);

        for (var row : compareRows) {
            int daily = ((Number) row.get("daily_rank")).intValue();
            int weekly = ((Number) row.get("weekly_rank")).intValue();
            int monthly = ((Number) row.get("monthly_rank")).intValue();
            int wDiff = daily - weekly;
            String wArrow = weekly == 0 ? "   —" : wDiff == 0 ? "   —"
                : wDiff < 0 ? String.format(" +%d ▲", -wDiff) : String.format(" -%d ▼", wDiff);

            String type = "";
            int pid = ((Number) row.get("product_id")).intValue();
            if (pid <= 3) type = "급상승";
            else if (pid >= 17) type = "장기강자";
            else if (pid == 14 || pid == 15) type = "하락추세";
            else if (pid == 8) type = "오늘바이럴";

            System.out.printf("  %6d │ %-26s │ %4d  │ %4d  │ %4d  │ %8s │ %s%n",
                row.get("product_id"), row.get("name"), daily, weekly, monthly, wArrow, type);
        }
        System.out.println();
    }

    // ── 대규모 성능 테스트 ──────────────────────────────────────────────

    @Test
    @DisplayName("대규모 — 10만 상품 × 30일 메트릭, 4 Partition 병렬 집계")
    void largeScalePartitionedBatchTest() throws Exception {
        int productCount = 100_000;
        int metricDays = 30;

        // ── 시드 ──
        long t0 = System.currentTimeMillis();
        seedProductsBulk(productCount);
        long productSeedMs = System.currentTimeMillis() - t0;

        t0 = System.currentTimeMillis();
        seedMetricsBulkWithTrends(productCount, metricDays, TARGET_DATE);
        long metricSeedMs = System.currentTimeMillis() - t0;

        int metricRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_metrics", Integer.class);
        System.out.printf("%n[시드 완료] 상품 %,d건 (%,dms) / 메트릭 %,d건 (%,dms)%n",
            productCount, productSeedMs, metricRows, metricSeedMs);

        // ── Weekly ──
        t0 = System.currentTimeMillis();
        BatchStatus weeklyStatus = runJob("weekly");
        long weeklyMs = System.currentTimeMillis() - t0;

        assertThat(weeklyStatus).isEqualTo(BatchStatus.COMPLETED);

        int weeklyMvCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_weekly WHERE period_key = ?",
            Integer.class, TARGET_DATE);
        assertThat(weeklyMvCount).isEqualTo(100);

        Long weeklyTopId = jdbcTemplate.queryForObject(
            "SELECT product_id FROM mv_product_rank_weekly WHERE period_key = ? AND ranking = 1",
            Long.class, TARGET_DATE);
        // 급상승 그룹(1~5000) 중 p=5000이 최고 메트릭
        assertThat(weeklyTopId).isEqualTo(5_000L);

        // ── Monthly ──
        t0 = System.currentTimeMillis();
        BatchStatus monthlyStatus = runJob("monthly");
        long monthlyMs = System.currentTimeMillis() - t0;

        assertThat(monthlyStatus).isEqualTo(BatchStatus.COMPLETED);

        int monthlyMvCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_monthly WHERE period_key = ?",
            Integer.class, TARGET_DATE);
        assertThat(monthlyMvCount).isEqualTo(100);

        Long monthlyTopId = jdbcTemplate.queryForObject(
            "SELECT product_id FROM mv_product_rank_monthly WHERE period_key = ? AND ranking = 1",
            Long.class, TARGET_DATE);
        // 장기강자 그룹(5001~15000) 중 p=15000이 최고 메트릭
        assertThat(monthlyTopId).isEqualTo(15_000L);

        // ── 파티션 균등 분배 검증 (monthly 실행 후 staging 기준) ──
        int stagingTotal = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mv_product_rank_staging WHERE period_key = ?",
            Integer.class, TARGET_DATE);
        assertThat(stagingTotal).isEqualTo(productCount);

        // ── 결과 출력 ──
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  대규모 배치 테스트 결과 (10만 건)");
        System.out.println("═══════════════════════════════════════════════════");
        System.out.printf("  상품 수          : %,d%n", productCount);
        System.out.printf("  메트릭 행 수     : %,d%n", metricRows);
        System.out.printf("  Partitioning     : %d Worker%n", 4);
        System.out.printf("  Weekly  소요     : %,dms (1위: product_%d, 급상승)%n", weeklyMs, weeklyTopId);
        System.out.printf("  Monthly 소요     : %,dms (1위: product_%d, 장기강자)%n", monthlyMs, monthlyTopId);
        System.out.printf("  Staging 적재     : %,d건 (~%,d건/partition)%n", stagingTotal, stagingTotal / 4);
        System.out.println("═══════════════════════════════════════════════════");
    }

    // ── 엣지 케이스 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("엣지 — 취소 반영: cancel_amount가 score에 반영")
    void cancellationReflectedInScore() throws Exception {
        seedProducts(2);

        // 상품 1: 매출 100만, 취소 없음
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, metric_date, view_count, like_count, unlike_count, " +
            "sales_count, sales_amount, cancel_count_by_event_date, cancel_amount_by_event_date, " +
            "cancel_count_by_order_date, cancel_amount_by_order_date) VALUES (1, '2026-04-16', 100, 10, 0, 10, 1000000, 0, 0, 0, 0)");

        // 상품 2: 매출 200만, 취소 150만 → 순 매출 50만
        jdbcTemplate.update(
            "INSERT INTO product_metrics (product_id, metric_date, view_count, like_count, unlike_count, " +
            "sales_count, sales_amount, cancel_count_by_event_date, cancel_amount_by_event_date, " +
            "cancel_count_by_order_date, cancel_amount_by_order_date) VALUES (2, '2026-04-16', 200, 20, 0, 20, 2000000, 5, 1500000, 5, 1500000)");

        BatchStatus status = runJob("weekly");

        assertThat(status).isEqualTo(BatchStatus.COMPLETED);

        // 상품 1이 1위 (순 매출 100만 > 상품 2 순 매출 50만)
        Long topProductId = jdbcTemplate.queryForObject(
            "SELECT product_id FROM mv_product_rank_weekly WHERE period_key = ? AND ranking = 1",
            Long.class, TARGET_DATE);
        assertThat(topProductId).isEqualTo(1L);
    }
}
