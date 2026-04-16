package com.loopers.application.ranking;

import com.loopers.application.product.ProductAppService;
import com.loopers.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RankingFacade {

    private final RankingAppService rankingAppService;
    private final MvRankingAppService mvRankingAppService;
    private final ProductAppService productAppService;

    public List<RankingInfo> getTopRankings(String date, int page, int size) {
        List<RankingEntry> entries = rankingAppService.getTopRankings(date, page, size);
        return enrichWithProductInfo(entries);
    }

    public List<RankingInfo> getWeeklyTopRankings(String yearWeek, int page, int size) {
        List<RankingEntry> entries = mvRankingAppService.getWeeklyRankings(yearWeek, page, size);
        return enrichWithProductInfo(entries);
    }

    public List<RankingInfo> getMonthlyTopRankings(String yearMonth, int page, int size) {
        List<RankingEntry> entries = mvRankingAppService.getMonthlyRankings(yearMonth, page, size);
        return enrichWithProductInfo(entries);
    }

    public List<RankingInfo> getHourlyTopRankings(String hour, int page, int size) {
        List<RankingEntry> entries = rankingAppService.getHourlyTopRankings(hour, page, size);
        return enrichWithProductInfo(entries);
    }

    public Long getProductRank(String date, Long productId) {
        return rankingAppService.getProductRank(date, productId);
    }

    private List<RankingInfo> enrichWithProductInfo(List<RankingEntry> entries) {
        if (entries.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = entries.stream()
                .map(RankingEntry::productId)
                .toList();

        Map<Long, Product> productMap = productAppService.getByIds(productIds);

        return entries.stream()
                .filter(entry -> productMap.containsKey(entry.productId()))
                .map(entry -> RankingInfo.of(entry, productMap.get(entry.productId())))
                .toList();
    }
}
