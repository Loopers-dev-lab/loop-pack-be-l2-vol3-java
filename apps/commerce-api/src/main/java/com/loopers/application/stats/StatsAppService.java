package com.loopers.application.stats;

import com.loopers.domain.stats.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 운영 통계 Application Service.
 * 도메인 서비스를 호출하고 StatsProjection → StatsInfo 변환을 수행한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsAppService {

    private final StatsService statsService;

    public StatsInfo.Overview getOverview(LocalDate startAt, LocalDate endAt) {
        return StatsInfo.Overview.from(statsService.getOverview(startAt, endAt));
    }

    public List<StatsInfo.DailyOrderStat> getDailyOrderStats(LocalDate startAt, LocalDate endAt) {
        return statsService.getDailyOrderStats(startAt, endAt).stream()
                .map(StatsInfo.DailyOrderStat::from)
                .toList();
    }

    public List<StatsInfo.ProductStat> getTopLikedProducts(int limit) {
        return statsService.getTopLikedProducts(limit).stream()
                .map(StatsInfo.ProductStat::from)
                .toList();
    }

    public List<StatsInfo.ProductStat> getTopOrderedProducts(int limit) {
        return statsService.getTopOrderedProducts(limit).stream()
                .map(StatsInfo.ProductStat::from)
                .toList();
    }

    public List<StatsInfo.LowStockProduct> getLowStockProducts(int threshold) {
        return statsService.getLowStockProducts(threshold).stream()
                .map(StatsInfo.LowStockProduct::from)
                .toList();
    }
}
