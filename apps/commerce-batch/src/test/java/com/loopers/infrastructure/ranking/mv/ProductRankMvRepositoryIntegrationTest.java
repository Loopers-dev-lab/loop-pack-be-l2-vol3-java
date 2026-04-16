package com.loopers.infrastructure.ranking.mv;

import com.loopers.domain.ranking.mv.ProductRankMonthlyRepository;
import com.loopers.domain.ranking.mv.ProductRankMvRow;
import com.loopers.domain.ranking.mv.ProductRankWeeklyRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.batch.job.enabled=false",
        "outbox.relay.enabled=false"
})
@Import(MySqlTestContainersConfig.class)
class ProductRankMvRepositoryIntegrationTest {

    @Autowired
    private ProductRankWeeklyRepository weeklyRepository;

    @Autowired
    private ProductRankMonthlyRepository monthlyRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("주간 MV: 동일 period에서 rank 오름차순으로 조회한다.")
    @Transactional
    void weekly_findByPeriodKeyOrderByRankAsc_samePeriod_returnsOrdered() {
        // given
        String periodKey = "2026W15";
        Instant at = Instant.parse("2026-04-10T00:00:00Z");
        weeklyRepository.save(ProductRankMvRow.newRow(periodKey, 200L, 2, new BigDecimal("12.5"), 1, at));
        weeklyRepository.save(ProductRankMvRow.newRow(periodKey, 100L, 1, new BigDecimal("99.0"), 1, at));

        // when
        List<ProductRankMvRow> rows = weeklyRepository.findByPeriodKeyOrderByRankAsc(periodKey);

        // then
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).productId()).isEqualTo(100L);
        assertThat(rows.get(0).rank()).isEqualTo(1);
        assertThat(rows.get(1).productId()).isEqualTo(200L);
        assertThat(rows.get(1).rank()).isEqualTo(2);
    }

    @Test
    @DisplayName("월간 MV: 동일 period에서 rank 오름차순으로 조회한다.")
    @Transactional
    void monthly_findByPeriodKeyOrderByRankAsc_samePeriod_returnsOrdered() {
        // given
        String periodKey = "202604";
        Instant at = Instant.parse("2026-04-10T00:00:00Z");
        monthlyRepository.save(ProductRankMvRow.newRow(periodKey, 20L, 2, new BigDecimal("3.25"), 1, at));
        monthlyRepository.save(ProductRankMvRow.newRow(periodKey, 10L, 1, new BigDecimal("8.00"), 1, at));

        // when
        List<ProductRankMvRow> rows = monthlyRepository.findByPeriodKeyOrderByRankAsc(periodKey);

        // then
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).productId()).isEqualTo(10L);
        assertThat(rows.get(1).productId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("주간 MV: UNIQUE(period_key, product_id) 위반 시 저장이 실패한다.")
    void weekly_save_duplicatePeriodAndProduct_shouldFail() {
        // given
        String periodKey = "2026W16";
        Instant at = Instant.parse("2026-05-01T00:00:00Z");
        weeklyRepository.save(ProductRankMvRow.newRow(periodKey, 1L, 1, BigDecimal.ONE, 1, at));

        // when // then
        assertThatThrownBy(() -> weeklyRepository.save(ProductRankMvRow.newRow(periodKey, 1L, 2, BigDecimal.TEN, 1, at)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("월간 MV: UNIQUE(period_key, product_id) 위반 시 저장이 실패한다.")
    void monthly_save_duplicatePeriodAndProduct_shouldFail() {
        // given
        String periodKey = "202605";
        Instant at = Instant.parse("2026-05-01T00:00:00Z");
        monthlyRepository.save(ProductRankMvRow.newRow(periodKey, 1L, 1, BigDecimal.ONE, 1, at));

        // when // then
        assertThatThrownBy(() -> monthlyRepository.save(ProductRankMvRow.newRow(periodKey, 1L, 2, BigDecimal.TEN, 1, at)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
