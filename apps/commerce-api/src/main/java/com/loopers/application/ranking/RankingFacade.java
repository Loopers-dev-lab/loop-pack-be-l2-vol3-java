package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductService;
import com.loopers.application.ranking.RankingInfo.RankingItem;
import com.loopers.domain.product.Product;
import com.loopers.domain.ranking.RankEntry;
import com.loopers.domain.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RankingFacade {

    private final RankingService rankingService;
    private final ProductService productService;

    // Query

    @Transactional(readOnly = true)
    public RankingInfo getRankings(RankingPeriod period, LocalDate date, int page, int size, Long userId) {
        String group = rankingService.resolveGroup(userId);
        List<RankEntry> entries = rankingService.getRankEntries(period, date, page, size, group);
        long totalCount = rankingService.getTotalCount(period, date, group);

        if (entries.isEmpty()) {
            return new RankingInfo(period, date, page, size, totalCount, group, List.of());
        }

        Set<Long> productIds = entries.stream()
                .map(RankEntry::productId)
                .collect(Collectors.toSet());
        Map<Long, Product> productMap = productService.getProductsMapByIds(productIds);

        List<RankingItem> items = entries.stream()
                .filter(e -> productMap.containsKey(e.productId()))
                .map(e -> {
                    Product product = productMap.get(e.productId());
                    return new RankingItem(
                            e.rank(),
                            e.score(),
                            toSimpleInfo(product)
                    );
                })
                .toList();

        return new RankingInfo(period, date, page, size, totalCount, group, items);
    }

    private ProductInfo toSimpleInfo(Product product) {
        return new ProductInfo(
                product.getId(),
                product.getBrandId(),
                null,
                product.getName(),
                product.getPrice(),
                null,
                product.getDescription(),
                product.getLikeCount(),
                product.isDeleted() ? ProductInfo.Status.DELETED : ProductInfo.Status.ACTIVE,
                product.getCreatedAt().toLocalDateTime(),
                product.getUpdatedAt().toLocalDateTime(),
                product.getDeletedAt() != null ? product.getDeletedAt().toLocalDateTime() : null
        );
    }
}
