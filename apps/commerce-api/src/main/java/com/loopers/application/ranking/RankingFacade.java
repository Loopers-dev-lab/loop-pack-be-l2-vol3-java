package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class RankingFacade {

    private static final String DAILY_CACHE_KEY_PREFIX  = "loopers:ranking:daily:";
    private static final String HOURLY_CACHE_KEY_PREFIX = "loopers:ranking:hourly:";

    private final RankingService rankingService;
    private final ProductService productService;
    private final BrandService brandService;
    private final RankingCacheRepository rankingCacheRepository;

    /**
     * 일간 랭킹 페이지 조회 (Cache-Aside).
     *
     * 1. Redis 캐시 히트 시 즉시 반환
     * 2. 캐시 미스 시: Redis ZSET → DB IN 쿼리 → 결과 캐시 저장
     */
    @Transactional(readOnly = true)
    public RankingResult findDailyRanking(LocalDate date, int page, int size) {
        String cacheKey = DAILY_CACHE_KEY_PREFIX + date + ":p" + page + ":s" + size;
        Optional<RankingResult> cached = rankingCacheRepository.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        RankingResult result = loadDailyRankingFromSource(date, page, size);
        rankingCacheRepository.save(cacheKey, result);
        return result;
    }

    private RankingResult loadDailyRankingFromSource(LocalDate date, int page, int size) {
        long offset = (long) page * size;
        long totalElements = rankingService.countDailyRanking(date);

        List<ZSetOperations.TypedTuple<String>> tuples =
                rankingService.findDailyRanking(date, offset, size);

        if (tuples.isEmpty()) {
            return new RankingResult(List.of(), page, size, totalElements);
        }

        List<Long> productIds = tuples.stream()
                .map(t -> Long.parseLong(t.getValue()))
                .toList();

        Map<Long, Product> productMap = productService.findAllByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> brandIds = productMap.values().stream()
                .map(Product::getBrandId).distinct().toList();
        Map<Long, String> brandNameMap = brandService.findNamesByIds(brandIds);

        List<RankingItem> items = new ArrayList<>();
        int rank = (int) offset + 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long productId = Long.parseLong(tuple.getValue());
            Product product = productMap.get(productId);
            if (product == null) {
                rank++;
                continue;  // 삭제된 상품은 건너뜀
            }
            String brandName = brandNameMap.getOrDefault(product.getBrandId(), "");
            items.add(new RankingItem(rank, ProductInfo.from(product, brandName), tuple.getScore()));
            rank++;
        }

        return new RankingResult(items, page, size, totalElements);
    }

    /**
     * 시간별 랭킹 페이지 조회 (Cache-Aside).
     *
     * 일간 랭킹과 동일한 구조, hourlyKey를 사용하는 점만 다름.
     */
    @Transactional(readOnly = true)
    public RankingResult findHourlyRanking(LocalDate date, int hour, int page, int size) {
        String cacheKey = HOURLY_CACHE_KEY_PREFIX + date + String.format("%02d", hour) + ":p" + page + ":s" + size;
        Optional<RankingResult> cached = rankingCacheRepository.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        RankingResult result = loadHourlyRankingFromSource(date, hour, page, size);
        rankingCacheRepository.save(cacheKey, result);
        return result;
    }

    private RankingResult loadHourlyRankingFromSource(LocalDate date, int hour, int page, int size) {
        long offset = (long) page * size;
        long totalElements = rankingService.countHourlyRanking(date, hour);

        List<ZSetOperations.TypedTuple<String>> tuples =
                rankingService.findHourlyRanking(date, hour, offset, size);

        if (tuples.isEmpty()) {
            return new RankingResult(List.of(), page, size, totalElements);
        }

        List<Long> productIds = tuples.stream()
                .map(t -> Long.parseLong(t.getValue()))
                .toList();

        Map<Long, Product> productMap = productService.findAllByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> brandIds = productMap.values().stream()
                .map(Product::getBrandId).distinct().toList();
        Map<Long, String> brandNameMap = brandService.findNamesByIds(brandIds);

        List<RankingItem> items = new ArrayList<>();
        int rank = (int) offset + 1;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            Long productId = Long.parseLong(tuple.getValue());
            Product product = productMap.get(productId);
            if (product == null) {
                rank++;
                continue;
            }
            String brandName = brandNameMap.getOrDefault(product.getBrandId(), "");
            items.add(new RankingItem(rank, ProductInfo.from(product, brandName), tuple.getScore()));
            rank++;
        }

        return new RankingResult(items, page, size, totalElements);
    }
}
