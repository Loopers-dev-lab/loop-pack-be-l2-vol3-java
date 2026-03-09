package com.loopers.domain.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatsService 도메인 서비스 테스트")
class StatsServiceTest {

    @Mock
    StatsRepository statsRepository;

    @InjectMocks
    StatsService statsService;

    @Test
    @DisplayName("주문 현황 요약이 올바르게 반환된다")
    void getOverview_ShouldReturnOrderStatusCounts() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        StatsProjection.Overview overview = StatsProjection.Overview.builder()
                .pendingCount(10).cancelledCount(3).expiredCount(2).build();
        when(statsRepository.getOverview(start, end)).thenReturn(overview);

        StatsProjection.Overview result = statsService.getOverview(start, end);

        assertThat(result.getPendingCount()).isEqualTo(10);
        assertThat(result.getCancelledCount()).isEqualTo(3);
        assertThat(result.getExpiredCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("일별 주문 통계가 올바르게 반환된다")
    void getDailyOrderStats_ShouldReturnDailyAggregation() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 7);
        List<StatsProjection.DailyOrderStat> stats = List.of(
                StatsProjection.DailyOrderStat.builder()
                        .date(LocalDate.of(2026, 1, 1))
                        .orderCount(5)
                        .totalAmount(BigDecimal.valueOf(50000))
                        .build()
        );
        when(statsRepository.getDailyOrderStats(start, end)).thenReturn(stats);

        List<StatsProjection.DailyOrderStat> result = statsService.getDailyOrderStats(start, end);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrderCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("좋아요 상위 N개 상품이 반환된다")
    void getTopLikedProducts_ShouldReturnTopN() {
        List<StatsProjection.ProductStat> stats = List.of(
                StatsProjection.ProductStat.builder()
                        .productId("p1").productName("인기상품").count(100).build()
        );
        when(statsRepository.getTopLikedProducts(10)).thenReturn(stats);

        List<StatsProjection.ProductStat> result = statsService.getTopLikedProducts(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCount()).isEqualTo(100);
    }

    @Test
    @DisplayName("주문 상위 N개 상품이 반환된다")
    void getTopOrderedProducts_ShouldReturnTopN() {
        List<StatsProjection.ProductStat> stats = List.of(
                StatsProjection.ProductStat.builder()
                        .productId("p1").productName("베스트상품").count(50).build()
        );
        when(statsRepository.getTopOrderedProducts(10)).thenReturn(stats);

        List<StatsProjection.ProductStat> result = statsService.getTopOrderedProducts(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCount()).isEqualTo(50);
    }

    @Test
    @DisplayName("재고 부족 상품이 반환된다")
    void getLowStockProducts_ShouldReturnBelowThreshold() {
        List<StatsProjection.LowStockProduct> stats = List.of(
                StatsProjection.LowStockProduct.builder()
                        .productId("p1").productName("부족상품")
                        .onHand(10).reserved(8).availableQty(2).build()
        );
        when(statsRepository.getLowStockProducts(5)).thenReturn(stats);

        List<StatsProjection.LowStockProduct> result = statsService.getLowStockProducts(5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAvailableQty()).isEqualTo(2);
    }
}
