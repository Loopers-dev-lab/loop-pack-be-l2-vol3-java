package com.loopers.batch.job.ranking.fixture;

import com.loopers.batch.job.ranking.RollingRankingJobConfig;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 시드 분포 검증 — 설계.md "트래픽 전제" 의 두 가지 핵심 invariant 를 자동 검증한다.
 * <ul>
 *     <li>Sleeping 70% 는 이벤트가 0 (활동 product = 전체의 30% 이하)</li>
 *     <li>Hot tier (상위 0.1%) 가 전체 이벤트의 30% 이상을 점유 (Zipf head)</li>
 * </ul>
 * 정확한 비율 (40/40/18/2) 은 Zipf α=1.2 한계상 ±편차가 큰데, 본질만 검증한다.
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
@TestPropertySource(properties = "spring.batch.job.name=" + RollingRankingJobConfig.JOB_NAME)
class BaselineSeederIntegrationTest {

    private static final LocalDate ANCHOR = LocalDate.of(2026, 4, 14);

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("시드 결과: Sleeping 70% 는 이벤트 0 → 활동 상품은 totalProducts × 30% 이하")
    @Test
    void sleepingTierProducesNoEvents() {
        SeedSpec spec = new SeedSpec(10_000, ANCHOR, 30, 42L);
        BaselineSeeder seeder = new BaselineSeeder(jdbcTemplate);

        BaselineSeeder.SeedReport report = seeder.seed(spec);

        long activeProducts = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT product_id) FROM product_view_metrics", Long.class);
        long maxProductId = jdbcTemplate.queryForObject(
                "SELECT MAX(product_id) FROM product_view_metrics", Long.class);

        assertAll(
                () -> assertThat(report.viewRowsInserted()).isPositive(),
                () -> assertThat(activeProducts).isLessThanOrEqualTo(spec.totalProducts() * 30L / 100),
                // Sleeping 영역 (rank ≥ 3000 = product_id ≥ 3001) 의 row 가 0
                () -> assertThat(maxProductId).isLessThanOrEqualTo(3000L)
        );
    }

    @DisplayName("시드 결과: Hot tier (상위 0.1%) 가 전체 view 이벤트의 30% 이상을 점유한다 (Zipf head)")
    @Test
    void hotTierDominatesEventVolume() {
        SeedSpec spec = new SeedSpec(10_000, ANCHOR, 30, 42L);
        BaselineSeeder seeder = new BaselineSeeder(jdbcTemplate);

        seeder.seed(spec);

        // Hot = 상위 0.1% = product_id 1~10
        Long hotEvents = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(view_count), 0) FROM product_view_metrics WHERE product_id <= 10",
                Long.class);
        Long totalEvents = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(view_count), 0) FROM product_view_metrics",
                Long.class);

        double hotShare = hotEvents.doubleValue() / totalEvents;
        assertThat(hotShare)
                .as("Hot tier share = " + hotShare)
                .isGreaterThanOrEqualTo(0.30);
    }

    @DisplayName("시드는 결정적이다 — 같은 seed 로 두 번 돌리면 row 수가 동일")
    @Test
    void seedIsDeterministic() {
        BaselineSeeder seeder = new BaselineSeeder(jdbcTemplate);

        BaselineSeeder.SeedReport first = seeder.seed(new SeedSpec(500, ANCHOR, 7, 42L));
        databaseCleanUp.truncateAllTables();
        BaselineSeeder.SeedReport second = seeder.seed(new SeedSpec(500, ANCHOR, 7, 42L));

        assertAll(
                () -> assertThat(second.viewRowsInserted()).isEqualTo(first.viewRowsInserted()),
                () -> assertThat(second.likeRowsInserted()).isEqualTo(first.likeRowsInserted()),
                () -> assertThat(second.orderRowsInserted()).isEqualTo(first.orderRowsInserted())
        );
    }

    @DisplayName("view : like : order = 10 : 1 : 0.1 비율로 시드된다")
    @Test
    void seedRatiosBetweenMetrics() {
        SeedSpec spec = new SeedSpec(1_000, ANCHOR, 7, 42L);
        BaselineSeeder seeder = new BaselineSeeder(jdbcTemplate);

        seeder.seed(spec);

        long viewSum  = sumColumn("product_view_metrics",  "view_count");
        long likeSum  = sumColumn("product_like_metrics",  "like_count");
        long orderSum = sumColumn("product_order_metrics", "quantity");

        // view 와 like 비율이 대략 10:1 부근 (정수 절단으로 일부 손실 허용)
        assertAll(
                () -> assertThat(likeSum).isLessThan(viewSum),
                () -> assertThat(orderSum).isLessThan(likeSum),
                () -> assertThat((double) viewSum / likeSum).isBetween(8.0, 12.0)
        );
    }

    private long sumColumn(String table, String column) {
        Long s = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(" + column + "), 0) FROM " + table, Long.class);
        return s == null ? 0L : s;
    }
}
