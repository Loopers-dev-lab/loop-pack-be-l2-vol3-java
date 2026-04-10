package com.loopers.domain.ranking;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RankingMetricsServiceIntegrationTest {

    private static final Long PRODUCT_ID_A = 100L;
    private static final Long PRODUCT_ID_B = 200L;
    private static final LocalDate TODAY = LocalDate.of(2026, 4, 9);
    private static final int HOUR_14 = 14;
    private static final int HOUR_15 = 15;

    @Autowired
    private RankingMetricsService rankingMetricsService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("UPSERT — view_count")
    @Nested
    class UpsertViewCount {

        @DisplayName("조회수를 처음 기록하면 행이 생성되고 dirty=true가 된다")
        @Test
        void firstInsert() {
            // act
            rankingMetricsService.incrementViewCount(PRODUCT_ID_A, TODAY, HOUR_14);

            // assert
            RankingMetricsSummary summary = rankingMetricsService.sumByProductIdAndDate(PRODUCT_ID_A, TODAY);
            assertThat(summary.totalViewCount()).isEqualTo(1);
            assertThat(summary.totalLikeCount()).isEqualTo(0);
            assertThat(summary.totalOrderRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @DisplayName("같은 상품·날짜·시간에 중복 UPSERT하면 view_count가 누적된다")
        @Test
        void duplicateUpsertAccumulates() {
            // act
            rankingMetricsService.incrementViewCount(PRODUCT_ID_A, TODAY, HOUR_14);
            rankingMetricsService.incrementViewCount(PRODUCT_ID_A, TODAY, HOUR_14);
            rankingMetricsService.incrementViewCount(PRODUCT_ID_A, TODAY, HOUR_14);

            // assert
            RankingMetricsSummary summary = rankingMetricsService.sumByProductIdAndDate(PRODUCT_ID_A, TODAY);
            assertThat(summary.totalViewCount()).isEqualTo(3);
        }

        @DisplayName("서로 다른 시간대에 기록하면 SUM이 합산된다")
        @Test
        void differentHoursSummed() {
            // act
            rankingMetricsService.incrementViewCount(PRODUCT_ID_A, TODAY, HOUR_14);
            rankingMetricsService.incrementViewCount(PRODUCT_ID_A, TODAY, HOUR_14);
            rankingMetricsService.incrementViewCount(PRODUCT_ID_A, TODAY, HOUR_15);

            // assert
            RankingMetricsSummary summary = rankingMetricsService.sumByProductIdAndDate(PRODUCT_ID_A, TODAY);
            assertThat(summary.totalViewCount()).isEqualTo(3);
        }
    }

    @DisplayName("UPSERT — like_count")
    @Nested
    class UpsertLikeCount {

        @DisplayName("좋아요 증가 후 차감하면 like_count가 감소한다")
        @Test
        void incrementAndDecrement() {
            // arrange
            rankingMetricsService.incrementLikeCount(PRODUCT_ID_A, TODAY, HOUR_14);
            rankingMetricsService.incrementLikeCount(PRODUCT_ID_A, TODAY, HOUR_14);

            // act
            rankingMetricsService.decrementLikeCount(PRODUCT_ID_A, TODAY, HOUR_14);

            // assert
            RankingMetricsSummary summary = rankingMetricsService.sumByProductIdAndDate(PRODUCT_ID_A, TODAY);
            assertThat(summary.totalLikeCount()).isEqualTo(1);
        }
    }

    @DisplayName("UPSERT — order_revenue")
    @Nested
    class UpsertOrderRevenue {

        @DisplayName("주문 매출이 누적된다")
        @Test
        void revenueAccumulated() {
            // act
            rankingMetricsService.addOrderRevenue(PRODUCT_ID_A, TODAY, HOUR_14, BigDecimal.valueOf(29900));
            rankingMetricsService.addOrderRevenue(PRODUCT_ID_A, TODAY, HOUR_14, BigDecimal.valueOf(15000));

            // assert
            RankingMetricsSummary summary = rankingMetricsService.sumByProductIdAndDate(PRODUCT_ID_A, TODAY);
            assertThat(summary.totalOrderRevenue()).isEqualByComparingTo(BigDecimal.valueOf(44900));
        }
    }

    @DisplayName("dirty 플래그 관리")
    @Nested
    class DirtyFlag {

        @DisplayName("UPSERT 후 dirty 상품 ID가 조회된다")
        @Test
        void dirtyProductFound() {
            // arrange
            rankingMetricsService.incrementViewCount(PRODUCT_ID_A, TODAY, HOUR_14);
            rankingMetricsService.incrementViewCount(PRODUCT_ID_B, TODAY, HOUR_14);

            // act
            Set<Long> dirtyProductIds = rankingMetricsService.findDirtyEntriesGroupedByProduct(TODAY).keySet();

            // assert
            assertThat(dirtyProductIds).containsExactlyInAnyOrder(PRODUCT_ID_A, PRODUCT_ID_B);
        }

        @DisplayName("clearDirtyByHour 후 해당 상품·시간은 dirty 목록에서 제외된다")
        @Test
        void clearDirtyByHourRemovesFromList() {
            // arrange
            rankingMetricsService.incrementViewCount(PRODUCT_ID_A, TODAY, HOUR_14);
            rankingMetricsService.incrementViewCount(PRODUCT_ID_B, TODAY, HOUR_14);

            // act
            rankingMetricsService.clearDirtyByHour(PRODUCT_ID_A, TODAY, HOUR_14);

            // assert
            Set<Long> dirtyProductIds = rankingMetricsService.findDirtyEntriesGroupedByProduct(TODAY).keySet();
            assertThat(dirtyProductIds).containsExactly(PRODUCT_ID_B);
        }

        @DisplayName("clearDirtyByHour 후 다시 UPSERT하면 dirty=true로 복구된다")
        @Test
        void upsertAfterClearResetsDirty() {
            // arrange
            rankingMetricsService.incrementViewCount(PRODUCT_ID_A, TODAY, HOUR_14);
            rankingMetricsService.clearDirtyByHour(PRODUCT_ID_A, TODAY, HOUR_14);

            // act
            rankingMetricsService.incrementViewCount(PRODUCT_ID_A, TODAY, HOUR_14);

            // assert
            Set<Long> dirtyProductIds = rankingMetricsService.findDirtyEntriesGroupedByProduct(TODAY).keySet();
            assertThat(dirtyProductIds).containsExactly(PRODUCT_ID_A);
        }
    }
}
