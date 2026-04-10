package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.application.ranking.RankingInfo.RankingItem;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankEntry;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private final RankingRedisRepository rankingRedisRepository;
    private final RankingKeyResolver keyResolver;
    private final ProductRepository productRepository;

    // Query

    @Transactional(readOnly = true)
    public RankingInfo getRankings(RankingPeriod period, LocalDate date, int page, int size) {
        String key = keyResolver.resolve(period, date);

        List<RankEntry> entries = rankingRedisRepository.getRankings(key, page, size);
        Long totalCount = rankingRedisRepository.getTotalCount(key);

        if (entries.isEmpty()) {
            return new RankingInfo(period, date, page, size,
                    totalCount == null ? 0 : totalCount, List.of());
        }

        Set<Long> productIds = entries.stream()
                .map(RankEntry::productId)
                .collect(Collectors.toSet());
        Map<Long, Product> productMap = productRepository.findAllByIdIn(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

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

        return new RankingInfo(period, date, page, size,
                totalCount == null ? 0 : totalCount, items);
    }

    public Integer getProductRank(Long productId, RankingPeriod period, LocalDate date) {
        String key = keyResolver.resolve(period, date);
        return rankingRedisRepository.getRank(key, productId);
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
