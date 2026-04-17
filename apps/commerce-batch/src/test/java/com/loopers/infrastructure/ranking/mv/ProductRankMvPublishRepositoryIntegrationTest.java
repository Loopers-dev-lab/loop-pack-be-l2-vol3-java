package com.loopers.infrastructure.ranking.mv;

import com.loopers.domain.ranking.mv.ProductRankMvPublishRepository;
import com.loopers.domain.ranking.mv.ProductRankMvRow;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "outbox.relay.enabled=false"
})
@Import(MySqlTestContainersConfig.class)
class ProductRankMvPublishRepositoryIntegrationTest {

    @Autowired
    private ProductRankMvPublishRepository productRankMvPublishRepository;

    @Autowired
    private MvProductRankWeeklyJpaRepository weeklyJpaRepository;

    @Autowired
    private MvProductRankMonthlyJpaRepository monthlyJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("주간 MV: 동일 period를 교체하면 이전 행이 사라지고 새 행만 남는다.")
    void replaceWeeklyPeriod_secondPublish_replacesRows() {
        Instant at = Instant.parse("2026-04-10T00:00:00Z");
        String periodKey = "2026W15";
        productRankMvPublishRepository.replaceWeeklyPeriod(
                periodKey,
                List.of(ProductRankMvRow.newRow(periodKey, 1L, 1, new BigDecimal("5"), 1, at)),
                at
        );
        assertThat(weeklyJpaRepository.findByPeriodKeyOrderByRankValueAsc(periodKey)).hasSize(1);

        productRankMvPublishRepository.replaceWeeklyPeriod(
                periodKey,
                List.of(
                        ProductRankMvRow.newRow(periodKey, 10L, 1, new BigDecimal("9"), 1, at),
                        ProductRankMvRow.newRow(periodKey, 20L, 2, new BigDecimal("1"), 1, at)
                ),
                at
        );
        var rows = weeklyJpaRepository.findByPeriodKeyOrderByRankValueAsc(periodKey);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getProductId()).isEqualTo(10L);
        assertThat(rows.get(1).getProductId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("월간 MV: 교체 후 rank 순으로 조회된다.")
    void replaceMonthlyPeriod_ordersByRank() {
        Instant at = Instant.parse("2026-04-10T00:00:00Z");
        String periodKey = "202604";
        productRankMvPublishRepository.replaceMonthlyPeriod(
                periodKey,
                List.of(
                        ProductRankMvRow.newRow(periodKey, 2L, 2, BigDecimal.ONE, 1, at),
                        ProductRankMvRow.newRow(periodKey, 1L, 1, BigDecimal.TEN, 1, at)
                ),
                at
        );
        var rows = monthlyJpaRepository.findByPeriodKeyOrderByRankValueAsc(periodKey);
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getProductId()).isEqualTo(1L);
        assertThat(rows.get(1).getProductId()).isEqualTo(2L);
    }
}
