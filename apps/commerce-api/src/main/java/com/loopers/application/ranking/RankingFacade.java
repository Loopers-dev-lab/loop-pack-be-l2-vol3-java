package com.loopers.application.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingService;
import com.loopers.support.enums.DisplayStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingFacade {

    private static final int BUFFER = 5;
    private static final long MAX_RANK = 100;
    private static final int WEEKLY_WINDOW_DAYS = 7;

    private final RankingService rankingService;
    private final ProductService productService;
    private final BrandService brandService;

    /**
     * period 분기 랭킹 조회. DAILY는 Redis ZSET, WEEKLY/MONTHLY는 MV.
     */
    public MvRankingPage getRankings(RankingPeriod period, LocalDate date, int page, int size) {
        return switch (period) {
            case DAILY -> getDailyRankings(date, page, size);
            case WEEKLY -> getWeeklyRankings(date, page, size);
            case MONTHLY -> getMonthlyRankings(YearMonth.from(date), page, size);
        };
    }

    /**
     * DAILY — Redis ZSET 기반 랭킹 (단일 진입점, 단일 캐시).
     */
    @Cacheable(value = "rankings", key = "'daily_' + #date + '_' + #page + '_' + #size",
            cacheManager = "rankingCacheManager")
    public MvRankingPage getDailyRankings(LocalDate date, int page, int size) {
        List<RankingRepository.RankingEntry> entries =
                rankingService.getTopRankings(date, page, size + BUFFER);

        long totalElements = Math.min(rankingService.getTotalCount(date), MAX_RANK);
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

        if (entries.isEmpty()) {
            return MvRankingPage.daily(date, page, size, 0, 0, List.of());
        }

        long baseRank = (long) page * size;
        List<RankingInfo> rankings = enrichDailyRankings(entries, size, baseRank);

        return MvRankingPage.daily(date, page, size, totalElements, totalPages, rankings);
    }

    /**
     * 시간 단위 Top-N 랭킹을 조회한다.
     * 실시간성이 핵심이라 캐시하지 않는다.
     */
    public List<RankingInfo> getHourlyRankings(String hourKey, int size) {
        List<RankingRepository.RankingEntry> entries =
                rankingService.getHourlyTopRankings(hourKey, size + BUFFER);

        if (entries.isEmpty()) {
            return List.of();
        }

        return enrichDailyRankings(entries, size, 0);
    }

    // ---- WEEKLY ----

    @Cacheable(value = "mvRankings", key = "'weekly_' + #baseDate + '_' + #page + '_' + #size",
            cacheManager = "mvRankingCacheManager")
    public MvRankingPage getWeeklyRankings(LocalDate baseDate, int page, int size) {
        List<RankingRepository.MvRankingEntry> entries =
                rankingService.getWeeklyTop(baseDate, page, size);

        long total = Math.min(rankingService.getWeeklyTotal(baseDate), MAX_RANK);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        LocalDateTime aggregatedAt = rankingService.getWeeklyAggregatedAt(baseDate).orElse(null);
        int windowDays = computeWeeklyWindowDays(baseDate);

        List<RankingInfo> items = entries.isEmpty() ? List.of() : enrichMvRankings(entries);

        return MvRankingPage.weekly(baseDate, windowDays, aggregatedAt,
                page, size, total, totalPages, items);
    }

    // ---- MONTHLY ----

    @Cacheable(value = "mvRankings", key = "'monthly_' + #yearMonth + '_' + #page + '_' + #size",
            cacheManager = "mvRankingCacheManager")
    public MvRankingPage getMonthlyRankings(YearMonth yearMonth, int page, int size) {
        List<RankingRepository.MvRankingEntry> entries =
                rankingService.getMonthlyTop(yearMonth, page, size);

        long total = Math.min(rankingService.getMonthlyTotal(yearMonth), MAX_RANK);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        LocalDateTime aggregatedAt = rankingService.getMonthlyAggregatedAt(yearMonth).orElse(null);

        List<RankingInfo> items = entries.isEmpty() ? List.of() : enrichMvRankings(entries);

        return MvRankingPage.monthly(yearMonth.toString(), aggregatedAt,
                page, size, total, totalPages, items);
    }

    // ---- 공통 enrich ----

    private List<RankingInfo> enrichDailyRankings(
            List<RankingRepository.RankingEntry> entries, int size, long baseRank) {
        List<Long> productIds = entries.stream()
                .map(RankingRepository.RankingEntry::productId)
                .toList();
        Map<Long, ProductModel> productMap = loadActiveProducts(productIds);
        Map<Long, BrandModel> brandMap = loadBrands(productMap);

        List<RankingInfo> rankings = new ArrayList<>();
        for (RankingRepository.RankingEntry entry : entries) {
            if (rankings.size() >= size) break;

            ProductModel product = productMap.get(entry.productId());
            if (product == null) continue;

            BrandModel brand = brandMap.get(product.getBrandId());
            rankings.add(RankingInfo.of(
                    baseRank + rankings.size() + 1,
                    entry.productId(),
                    product.getProductName(),
                    brand != null ? brand.getBrandName() : null,
                    product.getPrice(),
                    product.getImageUrl(),
                    entry.score()
            ));
        }
        return rankings;
    }

    private List<RankingInfo> enrichMvRankings(List<RankingRepository.MvRankingEntry> entries) {
        List<Long> productIds = entries.stream()
                .map(RankingRepository.MvRankingEntry::productId)
                .toList();
        Map<Long, ProductModel> productMap = loadActiveProducts(productIds);
        Map<Long, BrandModel> brandMap = loadBrands(productMap);

        List<RankingInfo> rankings = new ArrayList<>();
        for (RankingRepository.MvRankingEntry entry : entries) {
            ProductModel product = productMap.get(entry.productId());
            if (product == null) continue;

            BrandModel brand = brandMap.get(product.getBrandId());
            rankings.add(RankingInfo.of(
                    entry.rankNo(),
                    entry.productId(),
                    product.getProductName(),
                    brand != null ? brand.getBrandName() : null,
                    product.getPrice(),
                    product.getImageUrl(),
                    entry.score().doubleValue()
            ));
        }
        return rankings;
    }

    private Map<Long, ProductModel> loadActiveProducts(List<Long> productIds) {
        return productService.findAllByIds(productIds).stream()
                .filter(p -> !p.isDeleted())
                .filter(p -> p.getDisplayStatus() == DisplayStatus.ACTIVE)
                .collect(Collectors.toMap(ProductModel::getProductId, Function.identity()));
    }

    private Map<Long, BrandModel> loadBrands(Map<Long, ProductModel> productMap) {
        List<Long> brandIds = productMap.values().stream()
                .map(ProductModel::getBrandId)
                .distinct()
                .toList();
        return brandService.findAllByIds(brandIds).stream()
                .collect(Collectors.toMap(BrandModel::getBrandId, Function.identity()));
    }

    /**
     * 실제 MV가 커버하는 일 수. 배치 초회 직후 데이터가 7일 미만일 때
     * 클라이언트가 "진행 중" 배지를 표시할 수 있도록 한다.
     * 단순 구현: 항상 7 반환 (실제 윈도우 크기). 개선은 후속 과제.
     */
    private int computeWeeklyWindowDays(LocalDate baseDate) {
        return WEEKLY_WINDOW_DAYS;
    }
}
