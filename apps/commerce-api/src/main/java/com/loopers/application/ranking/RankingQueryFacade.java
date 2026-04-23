package com.loopers.application.ranking;

import com.loopers.application.brand.BrandApplicationService;
import com.loopers.application.product.ProductApplicationService;
import com.loopers.application.ranking.cache.RankingProductCacheItem;
import com.loopers.domain.product.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RankingQueryFacade {

    private final RankingApplicationService rankingApplicationService;
    private final ProductApplicationService productApplicationService;
    private final BrandApplicationService brandApplicationService;
    private final RankingProductCacheApplicationService rankingProductCacheApplicationService;

    public List<TopRankingProductView> getDailyPage(LocalDate metricDate, int page, int size) {
        return toTopRankingProducts(rankingApplicationService.getDailyPage(metricDate, page, size));
    }

    public List<TopRankingProductView> getHourlyPage(LocalDateTime metricHour, int page, int size) {
        return toTopRankingProducts(rankingApplicationService.getHourlyPage(metricHour, page, size));
    }

    public List<TopRankingProductView> getWeeklyPage(LocalDate snapshotDate, int page, int size) {
        return toTopRankingProducts(rankingApplicationService.getWeeklyPage(snapshotDate, page, size));
    }

    public List<TopRankingProductView> getMonthlyPage(LocalDate snapshotDate, int page, int size) {
        return toTopRankingProducts(rankingApplicationService.getMonthlyPage(snapshotDate, page, size));
    }

    public RankingProductView getProductRank(UUID productId) {
        productApplicationService.get(productId);
        return rankingApplicationService.getProductRank(productId);
    }

    public List<TopRankingProductView> toTopRankingProducts(List<RankingProductView> rankingViews) {
        if (rankingViews.isEmpty()) {
            return List.of();
        }
        List<UUID> productIds = rankingViews.stream().map(RankingProductView::productId).toList();
        Map<UUID, RankingProductCacheItem> rankingProductsById = new LinkedHashMap<>(rankingProductCacheApplicationService.findAll(productIds));
        List<UUID> missingProductIds = productIds.stream()
                .filter(productId -> !rankingProductsById.containsKey(productId))
                .toList();
        if (!missingProductIds.isEmpty()) {
            List<Product> missingProducts = productApplicationService.findAllByIds(missingProductIds);
            List<RankingProductCacheItem> cachedItems = missingProducts.stream()
                    .map(RankingProductCacheItem::from)
                    .toList();
            rankingProductCacheApplicationService.saveAll(cachedItems);
            cachedItems.forEach(item -> rankingProductsById.put(item.productId(), item));
        }
        Map<UUID, String> brandNames = brandApplicationService.findNamesByIds(
                rankingProductsById.values().stream().map(RankingProductCacheItem::brandId).toList()
        );
        return rankingViews.stream()
                .filter(rankingView -> rankingProductsById.containsKey(rankingView.productId()))
                .map(rankingView -> {
                    RankingProductCacheItem product = rankingProductsById.get(rankingView.productId());
                    return new TopRankingProductView(
                            product.productId(),
                            product.name(),
                            product.price(),
                            product.brandId(),
                            brandNames.get(product.brandId()),
                            product.likeCount(),
                            rankingView.rank(),
                            rankingView.score()
                    );
                })
                .toList();
    }
}
