package com.loopers.application.ranking;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("Quarterly — JOIN vs App-level Aggregation 비교")
class QuarterlyJoinVsAppAggregationTest {

    private static final Logger log = LoggerFactory.getLogger(QuarterlyJoinVsAppAggregationTest.class);
    private static final int PRODUCT_COUNT = 100;
    private static final int ITERATIONS = 50;
    private static final int WARMUP = 5;
    private static final String PERIOD_KEY = "20260416";

    private static final String JOIN_SQL = """
            SELECT mv.rank_no, mv.ref_product_id, mv.score,
                   p.product_id, p.product_name, p.price
            FROM mv_product_rank_quarterly mv
            INNER JOIN mv_product_rank_publication pub
                ON pub.period_type = 'QUARTERLY'
               AND pub.period_key = mv.period_key
               AND pub.published_version = mv.version
            INNER JOIN products p ON p.id = mv.ref_product_id
            WHERE mv.period_key = ?
            ORDER BY mv.rank_no ASC
            LIMIT ? OFFSET ?
            """;

    private static final String RANK_ONLY_SQL = """
            SELECT mv.rank_no, mv.ref_product_id, mv.score
            FROM mv_product_rank_quarterly mv
            INNER JOIN mv_product_rank_publication pub
                ON pub.period_type = 'QUARTERLY'
               AND pub.period_key = mv.period_key
               AND pub.published_version = mv.version
            WHERE mv.period_key = ?
            ORDER BY mv.rank_no ASC
            LIMIT ? OFFSET ?
            """;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        createMvTablesIfAbsent();
        seedProducts();
        seedRankMv();
        seedPublication();
    }

    private void createMvTablesIfAbsent() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mv_product_rank_quarterly (
                    period_key     VARCHAR(8)     NOT NULL,
                    version        BIGINT         NOT NULL DEFAULT 1,
                    rank_no        INT            NOT NULL,
                    ref_product_id BIGINT         NOT NULL,
                    score          DOUBLE         NOT NULL,
                    view_count     BIGINT         NOT NULL DEFAULT 0,
                    like_count     BIGINT         NOT NULL DEFAULT 0,
                    order_amount   DECIMAL(15,2)  NOT NULL DEFAULT 0,
                    created_at     DATETIME       NOT NULL,
                    updated_at     DATETIME       NOT NULL,
                    PRIMARY KEY (period_key, version, rank_no)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS mv_product_rank_publication (
                    period_type       VARCHAR(20)  NOT NULL,
                    period_key        VARCHAR(8)   NOT NULL,
                    published_version BIGINT       NOT NULL DEFAULT 0,
                    next_version      BIGINT       NOT NULL DEFAULT 0,
                    updated_at        DATETIME(6)  NOT NULL,
                    PRIMARY KEY (period_type, period_key)
                )
                """);
        jdbcTemplate.execute("DELETE FROM mv_product_rank_quarterly");
        jdbcTemplate.execute("DELETE FROM mv_product_rank_publication");
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("두 방식이 동일한 결과를 반환한다")
    void correctnessEquivalence() {
        List<QuarterlyRankViewRow> joinResult = fetchByJoin(PERIOD_KEY, 0, 100);
        List<QuarterlyRankViewRow> appResult = fetchByAppAggregation(PERIOD_KEY, 0, 100);

        assertThat(joinResult).hasSameSizeAs(appResult);
        for (int i = 0; i < joinResult.size(); i++) {
            assertThat(joinResult.get(i).rankNo()).isEqualTo(appResult.get(i).rankNo());
            assertThat(joinResult.get(i).productDbId()).isEqualTo(appResult.get(i).productDbId());
            assertThat(joinResult.get(i).productName()).isEqualTo(appResult.get(i).productName());
        }
    }

    @Test
    @DisplayName("성능 비교: JOIN vs App-level 2-step")
    void performanceBenchmark() {
        for (int i = 0; i < WARMUP; i++) {
            fetchByJoin(PERIOD_KEY, 0, 100);
            fetchByAppAggregation(PERIOD_KEY, 0, 100);
        }

        double joinAvgMs = benchmark(() -> fetchByJoin(PERIOD_KEY, 0, 100));
        double appAvgMs = benchmark(() -> fetchByAppAggregation(PERIOD_KEY, 0, 100));

        log.info("=== Quarterly JOIN vs App Aggregation Benchmark ===");
        log.info("Iterations: {}, Products: {}, TopN: 100", ITERATIONS, PRODUCT_COUNT);
        log.info("JOIN (native SQL):   avg={} ms", formatMs(joinAvgMs));
        log.info("APP (2-step fetch):  avg={} ms", formatMs(appAvgMs));
        log.info("Delta: {} ms ({} x)", formatMs(appAvgMs - joinAvgMs),
                String.format(Locale.ROOT, "%.2f", appAvgMs / joinAvgMs));
    }

    private double benchmark(Runnable target) {
        long totalNs = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            target.run();
            totalNs += (System.nanoTime() - start);
        }
        return (totalNs / (double) ITERATIONS) / 1_000_000.0;
    }

    private static String formatMs(double ms) {
        return String.format(Locale.ROOT, "%.3f", ms);
    }

    private List<QuarterlyRankViewRow> fetchByJoin(String periodKey, int offset, int size) {
        return jdbcTemplate.query(JOIN_SQL,
                (rs, i) -> new QuarterlyRankViewRow(
                        rs.getInt("rank_no"),
                        rs.getLong("ref_product_id"),
                        rs.getDouble("score"),
                        rs.getString("product_id"),
                        rs.getString("product_name"),
                        rs.getBigDecimal("price")
                ),
                periodKey, size, offset);
    }

    private List<QuarterlyRankViewRow> fetchByAppAggregation(String periodKey, int offset, int size) {
        List<QuarterlyRankViewRow> rankOnly = jdbcTemplate.query(RANK_ONLY_SQL,
                (rs, i) -> new QuarterlyRankViewRow(
                        rs.getInt("rank_no"),
                        rs.getLong("ref_product_id"),
                        rs.getDouble("score"),
                        null, null, null),
                periodKey, size, offset);

        if (rankOnly.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>(rankOnly.size());
        for (QuarterlyRankViewRow r : rankOnly) {
            ids.add(r.productDbId());
        }

        Map<Long, ProductModel> productMap = new HashMap<>();
        for (ProductModel p : productRepository.findAllByIdIncludingDeleted(ids)) {
            productMap.put(p.getId(), p);
        }

        List<QuarterlyRankViewRow> result = new ArrayList<>(rankOnly.size());
        for (QuarterlyRankViewRow r : rankOnly) {
            ProductModel p = productMap.get(r.productDbId());
            result.add(new QuarterlyRankViewRow(
                    r.rankNo(), r.productDbId(), r.score(),
                    p == null ? null : p.getProductId().value(),
                    p == null ? null : p.getProductName().value(),
                    p == null ? null : p.getPrice().value()
            ));
        }
        return result;
    }

    private void seedProducts() {
        for (int i = 1; i <= PRODUCT_COUNT; i++) {
            productRepository.save(ProductModel.create(
                    String.format("P%04d", i),
                    1L,
                    "Product " + i,
                    new BigDecimal(1000 + i * 10),
                    100
            ));
        }
    }

    private void seedRankMv() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM products ORDER BY id ASC LIMIT ?", Long.class, PRODUCT_COUNT);
        String sql = """
                INSERT INTO mv_product_rank_quarterly
                    (period_key, version, rank_no, ref_product_id, score,
                     view_count, like_count, order_amount, created_at, updated_at)
                VALUES (?, 1, ?, ?, ?, 0, 0, 0, NOW(), NOW())
                """;
        for (int i = 0; i < ids.size(); i++) {
            jdbcTemplate.update(sql, PERIOD_KEY, i + 1, ids.get(i), (PRODUCT_COUNT - i) * 10.0);
        }
    }

    private void seedPublication() {
        jdbcTemplate.update("""
                INSERT INTO mv_product_rank_publication
                    (period_type, period_key, published_version, next_version, updated_at)
                VALUES ('QUARTERLY', ?, 1, 1, ?)
                """, PERIOD_KEY, Instant.now());
    }
}
