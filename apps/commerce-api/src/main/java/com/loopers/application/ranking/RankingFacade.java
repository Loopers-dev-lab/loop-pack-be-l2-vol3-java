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
    private final ProductAppService productAppService;

    public List<RankingInfo> getTopRankings(String date, int page, int size) {
        List<RankingEntry> entries = rankingAppService.getTopRankings(date, page, size);

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

    public List<RankingInfo> getHourlyTopRankings(String hour, int page, int size) {
        List<RankingEntry> entries = rankingAppService.getHourlyTopRankings(hour, page, size);

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

    public Long getProductRank(String date, Long productId) {
        return rankingAppService.getProductRank(date, productId);
    }
}
