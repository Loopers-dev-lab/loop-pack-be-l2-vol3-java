package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.MonthlyRank;
import com.loopers.domain.ranking.MonthlyRankRepository;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingService;
import com.loopers.domain.ranking.WeeklyRank;
import com.loopers.domain.ranking.WeeklyRankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
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
    private final WeeklyRankRepository weeklyRankRepository;
    private final MonthlyRankRepository monthlyRankRepository;
    private final StringRedisTemplate stringRedisTemplate;

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

        List<RankingEntry> entries = rankingService.findDailyRanking(date, offset, size);

        if (entries.isEmpty()) {
            return new RankingResult(List.of(), page, size, totalElements);
        }

        List<Long> productIds = entries.stream().map(RankingEntry::productId).toList();

        Map<Long, Product> productMap = productService.findAllByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> brandIds = productMap.values().stream()
                .map(Product::getBrandId).distinct().toList();
        Map<Long, String> brandNameMap = brandService.findNamesByIds(brandIds);

        List<RankingItem> items = new ArrayList<>();
        int rank = (int) offset + 1;
        for (RankingEntry entry : entries) {
            Product product = productMap.get(entry.productId());
            if (product == null) {
                rank++;
                continue;
            }
            String brandName = brandNameMap.getOrDefault(product.getBrandId(), "");
            items.add(new RankingItem(rank, ProductInfo.from(product, brandName), entry.score()));
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

        List<RankingEntry> entries = rankingService.findHourlyRanking(date, hour, offset, size);

        if (entries.isEmpty()) {
            return new RankingResult(List.of(), page, size, totalElements);
        }

        List<Long> productIds = entries.stream().map(RankingEntry::productId).toList();

        Map<Long, Product> productMap = productService.findAllByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> brandIds = productMap.values().stream()
                .map(Product::getBrandId).distinct().toList();
        Map<Long, String> brandNameMap = brandService.findNamesByIds(brandIds);

        List<RankingItem> items = new ArrayList<>();
        int rank = (int) offset + 1;
        for (RankingEntry entry : entries) {
            Product product = productMap.get(entry.productId());
            if (product == null) {
                rank++;
                continue;
            }
            String brandName = brandNameMap.getOrDefault(product.getBrandId(), "");
            items.add(new RankingItem(rank, ProductInfo.from(product, brandName), entry.score()));
            rank++;
        }

        return new RankingResult(items, page, size, totalElements);
    }

    /**
     * 주간 랭킹 페이지 조회 (Cache-Aside).
     * date 미지정 시 Redis latest_date → DB MAX() 순으로 폴백.
     */
    @Transactional(readOnly = true)
    public RankingResult findWeeklyRanking(LocalDate date, int page, int size) {
        LocalDate snapshot = resolveSnapshotDate(date, "rankings:weekly:latest_date",
            weeklyRankRepository::findLatestSnapshotDate);
        if (snapshot == null) {
            return new RankingResult(List.of(), page, size, 0L);
        }

        String cacheKey = "rankings:weekly:%s:%d:%d".formatted(snapshot, page, size);
        Optional<RankingResult> cached = rankingCacheRepository.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        Page<WeeklyRank> ranks = weeklyRankRepository.findBySnapshotDateOrderByRankAsc(
            snapshot, PageRequest.of(page, size));
        RankingResult result = toRankingResult(ranks.getContent(), r -> r.getRank(),
            r -> r.getProductId(), r -> r.getScore(), page, size, ranks.getTotalElements());
        rankingCacheRepository.save(cacheKey, result);
        return result;
    }

    /**
     * 월간 랭킹 페이지 조회 (Cache-Aside).
     * date 미지정 시 Redis latest_date → DB MAX() 순으로 폴백.
     */
    @Transactional(readOnly = true)
    public RankingResult findMonthlyRanking(LocalDate date, int page, int size) {
        LocalDate snapshot = resolveSnapshotDate(date, "rankings:monthly:latest_date",
            monthlyRankRepository::findLatestSnapshotDate);
        if (snapshot == null) {
            return new RankingResult(List.of(), page, size, 0L);
        }

        String cacheKey = "rankings:monthly:%s:%d:%d".formatted(snapshot, page, size);
        Optional<RankingResult> cached = rankingCacheRepository.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        Page<MonthlyRank> ranks = monthlyRankRepository.findBySnapshotDateOrderByRankAsc(
            snapshot, PageRequest.of(page, size));
        RankingResult result = toRankingResult(ranks.getContent(), r -> r.getRank(),
            r -> r.getProductId(), r -> r.getScore(), page, size, ranks.getTotalElements());
        rankingCacheRepository.save(cacheKey, result);
        return result;
    }

    private <T> RankingResult toRankingResult(
            List<T> rows,
            Function<T, Integer> rankExtractor,
            Function<T, Long> productIdExtractor,
            Function<T, Double> scoreExtractor,
            int page, int size, long totalElements) {

        List<Long> productIds = rows.stream().map(productIdExtractor).toList();
        Map<Long, Product> productMap = productService.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<Long> brandIds = productMap.values().stream()
            .map(Product::getBrandId).distinct().toList();
        Map<Long, String> brandNameMap = brandService.findNamesByIds(brandIds);

        List<RankingItem> items = rows.stream()
            .map(row -> {
                Long productId = productIdExtractor.apply(row);
                Product product = productMap.get(productId);
                if (product == null) return null;
                String brandName = brandNameMap.getOrDefault(product.getBrandId(), "");
                return new RankingItem(rankExtractor.apply(row), ProductInfo.from(product, brandName),
                    scoreExtractor.apply(row));
            })
            .filter(item -> item != null)
            .toList();

        return new RankingResult(items, page, size, totalElements);
    }

    private LocalDate resolveSnapshotDate(LocalDate given, String latestKey,
            Supplier<Optional<LocalDate>> dbFallback) {
        if (given != null) return given;

        String cached = stringRedisTemplate.opsForValue().get(latestKey);
        if (cached != null) return LocalDate.parse(cached);

        Optional<LocalDate> fromDb = dbFallback.get();
        fromDb.ifPresent(d -> stringRedisTemplate.opsForValue().set(latestKey, d.toString(), Duration.ofHours(25)));
        return fromDb.orElse(null);
    }
}
