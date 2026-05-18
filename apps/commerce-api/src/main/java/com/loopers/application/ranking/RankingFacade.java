package com.loopers.application.ranking;

import com.loopers.application.product.ProductDto;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.mv.MvProductRankMonthly;
import com.loopers.domain.ranking.mv.MvProductRankWeekly;
import com.loopers.domain.ranking.mv.MvRankingRepository;
import com.loopers.infrastructure.ranking.RankingCacheStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingFacade {

    private static final String RANKING_KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingRepository rankingRepository;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final RankingCacheStore rankingCacheStore;
    private final MvRankingRepository mvRankingRepository;

    @Transactional(readOnly = true)
    public List<RankingDto.RankingItemInfo> getRankings(String period, String date, int page, int size) {
        return rankingCacheStore.getRankings(period, date, page, size)
            .orElseGet(() -> {
                List<RankingDto.RankingItemInfo> result = switch (period) {
                    case "weekly" -> loadMvRankings(date, page, size, true);
                    case "monthly" -> loadMvRankings(date, page, size, false);
                    default -> loadRankings(date, page, size);
                };
                if (!result.isEmpty()) {
                    rankingCacheStore.putRankings(period, date, page, size, result);
                }
                return result;
            });
    }

    private List<RankingDto.RankingItemInfo> loadRankings(String date, int page, int size) {
        String key = RANKING_KEY_PREFIX + date;
        long start = (long) page * size;
        int fetchSize = size + 10; // 삭제된 상품 대비 여유분

        List<RankingEntry> entries = rankingRepository.getTopRankings(key, start, start + fetchSize - 1);
        if (entries.isEmpty()) {
            return List.of();
        }

        Set<Long> productIds = entries.stream()
            .map(RankingEntry::productId)
            .collect(Collectors.toSet());

        Map<Long, Product> productMap = productRepository.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        Set<Long> brandIds = productMap.values().stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());

        Map<Long, String> brandNameMap = brandRepository.findAllByIds(brandIds).stream()
            .collect(Collectors.toMap(Brand::getId, Brand::getName));

        List<RankingDto.RankingItemInfo> result = new ArrayList<>();
        long rank = start + 1;
        for (RankingEntry entry : entries) {
            if (result.size() >= size) {
                break;
            }
            Product product = productMap.get(entry.productId());
            if (product == null) {
                continue;
            }
            String brandName = brandNameMap.getOrDefault(product.getBrandId(), "");
            ProductDto.ProductInfo productInfo = ProductDto.ProductInfo.of(product, brandName);
            result.add(RankingDto.RankingItemInfo.of(rank++, entry.score(), productInfo));
        }
        return result;
    }

    private List<RankingDto.RankingItemInfo> loadMvRankings(String date, int page, int size, boolean weekly) {
        LocalDate aggregatedAt = LocalDate.parse(date, DATE_FORMAT);

        List<Long> productIds;
        List<Integer> ranks;
        List<Double> scores;

        if (weekly) {
            var mvRanks = mvRankingRepository.findWeeklyRankings(aggregatedAt, page, size);
            productIds = mvRanks.stream().map(MvProductRankWeekly::getProductId).toList();
            ranks = mvRanks.stream().map(MvProductRankWeekly::getRank).toList();
            scores = mvRanks.stream().map(MvProductRankWeekly::getScore).toList();
        } else {
            var mvRanks = mvRankingRepository.findMonthlyRankings(aggregatedAt, page, size);
            productIds = mvRanks.stream().map(MvProductRankMonthly::getProductId).toList();
            ranks = mvRanks.stream().map(MvProductRankMonthly::getRank).toList();
            scores = mvRanks.stream().map(MvProductRankMonthly::getScore).toList();
        }

        if (productIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Product> productMap = productRepository.findAllByIds(Set.copyOf(productIds)).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        Set<Long> brandIds = productMap.values().stream()
            .map(Product::getBrandId)
            .collect(Collectors.toSet());

        Map<Long, String> brandNameMap = brandRepository.findAllByIds(brandIds).stream()
            .collect(Collectors.toMap(Brand::getId, Brand::getName));

        List<RankingDto.RankingItemInfo> result = new ArrayList<>();
        for (int i = 0; i < productIds.size(); i++) {
            Product product = productMap.get(productIds.get(i));
            if (product == null) {
                continue;
            }
            String brandName = brandNameMap.getOrDefault(product.getBrandId(), "");
            ProductDto.ProductInfo productInfo = ProductDto.ProductInfo.of(product, brandName);
            result.add(RankingDto.RankingItemInfo.of(ranks.get(i), scores.get(i), productInfo));
        }
        return result;
    }

    public RankingDto.ProductRankInfo getProductRank(Long productId, String date) {
        String key = RANKING_KEY_PREFIX + date;
        Long rank = rankingRepository.getRank(key, productId);
        if (rank == null) {
            return new RankingDto.ProductRankInfo(null, null);
        }
        Double score = rankingRepository.getScore(key, productId);
        return new RankingDto.ProductRankInfo(rank + 1, score);
    }

    public String todayDate() {
        return LocalDate.now(ZoneId.of("Asia/Seoul")).format(DATE_FORMAT);
    }
}
