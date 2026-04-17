package com.loopers.batch.job.ranking.fixture;

import com.loopers.batch.job.ranking.RollingRankingJobConfig;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
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
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BaselineSeederIntegrationTest {

    private static final LocalDate ANCHOR = LocalDate.of(2026, 4, 14);

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 트래픽_분포 {

        @Test
        void Sleeping_70퍼센트는_이벤트_0이므로_활동_상품은_전체의_30퍼센트_이하이다() {
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

        @Test
        void Hot_tier_상위_0_1퍼센트가_전체_view_이벤트의_30퍼센트_이상을_점유한다() {
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
    }

    @Nested
    class 결정성 {

        @Test
        void 같은_seed_로_두번_돌리면_row_수가_동일하다() {
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
    }

    @Nested
    class 메트릭_비율 {

        @Test
        void view_like_order_가_10_1_0_1_비율로_시드된다() {
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
    }

    private long sumColumn(String table, String column) {
        Long s = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(" + column + "), 0) FROM " + table, Long.class);
        return s == null ? 0L : s;
    }
}
