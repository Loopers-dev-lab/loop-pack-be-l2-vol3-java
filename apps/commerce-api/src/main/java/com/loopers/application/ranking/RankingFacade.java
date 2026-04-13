package com.loopers.application.ranking;

import com.loopers.application.product.ProductDto;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
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

    @Transactional(readOnly = true)
    public List<RankingDto.RankingItemInfo> getRankings(String date, int page, int size) {
        return rankingCacheStore.getRankings(date, page, size)
            .orElseGet(() -> {
                List<RankingDto.RankingItemInfo> result = loadRankings(date, page, size);
                if (!result.isEmpty()) {
                    rankingCacheStore.putRankings(date, page, size, result);
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
