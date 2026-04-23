package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.ranking.MonthlyRankingRepository;
import com.loopers.domain.ranking.ProductRankMonthlyModel;
import com.loopers.domain.ranking.ProductRankWeeklyModel;
import com.loopers.domain.ranking.WeeklyRankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RankingQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final RankingKeyGenerator rankingKeyGenerator;
    private final ProductService productService;
    private final WeeklyRankingRepository weeklyRankingRepository;
    private final MonthlyRankingRepository monthlyRankingRepository;

    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private final RedisTemplate<String, String> redisTemplate;

    public RankingPageInfo getDailyRanking(LocalDate date, int page, int size) {
        LocalDate targetDate = date == null ? rankingKeyGenerator.today() : date;
        RankingResult ranking = fetchRanking(rankingKeyGenerator.dailyKey(targetDate), page, size);
        return new RankingPageInfo(
            targetDate,
            ranking.page(),
            ranking.size(),
            ranking.totalElements(),
            ranking.totalPages(),
            ranking.content()
        );
    }

    public HourlyRankingPageInfo getHourlyRanking(LocalDateTime hour, int page, int size) {
        LocalDateTime targetHour = hour == null ? rankingKeyGenerator.currentHour() : hour;
        RankingResult ranking = fetchRanking(rankingKeyGenerator.hourlyKey(targetHour), page, size);
        return new HourlyRankingPageInfo(
            targetHour,
            ranking.page(),
            ranking.size(),
            ranking.totalElements(),
            ranking.totalPages(),
            ranking.content()
        );
    }

    public RankingPageInfo getWeeklyRanking(LocalDate date, int page, int size) {
        LocalDate targetDate = date == null ? rankingKeyGenerator.today() : date;
        String yearWeek = toYearWeek(targetDate);

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<ProductRankWeeklyModel> rankPage = weeklyRankingRepository.findByYearWeek(
            yearWeek, PageRequest.of(safePage - 1, safeSize)
        );

        List<Long> productIds = rankPage.getContent().stream()
            .map(ProductRankWeeklyModel::getProductId)
            .toList();

        Map<Long, ProductInfo> productInfoMap = new LinkedHashMap<>();
        for (ProductModel product : productService.getProductsByIds(productIds)) {
            productInfoMap.put(product.getId(), ProductInfo.from(product));
        }

        List<RankedProductInfo> rankedProducts = new ArrayList<>();
        for (ProductRankWeeklyModel rank : rankPage.getContent()) {
            ProductInfo productInfo = productInfoMap.get(rank.getProductId());
            if (productInfo != null) {
                rankedProducts.add(new RankedProductInfo(
                    rank.getRankNumber(),
                    rank.getScore(),
                    productInfo.withRanking((long) rank.getRankNumber())
                ));
            }
        }

        return new RankingPageInfo(
            targetDate,
            safePage,
            safeSize,
            rankPage.getTotalElements(),
            rankPage.getTotalPages(),
            rankedProducts
        );
    }

    public RankingPageInfo getMonthlyRanking(LocalDate date, int page, int size) {
        LocalDate targetDate = date == null ? rankingKeyGenerator.today() : date;
        String yearMonth = targetDate.format(YEAR_MONTH_FORMATTER);

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<ProductRankMonthlyModel> rankPage = monthlyRankingRepository.findByYearMonth(
            yearMonth, PageRequest.of(safePage - 1, safeSize)
        );

        List<Long> productIds = rankPage.getContent().stream()
            .map(ProductRankMonthlyModel::getProductId)
            .toList();

        Map<Long, ProductInfo> productInfoMap = new LinkedHashMap<>();
        for (ProductModel product : productService.getProductsByIds(productIds)) {
            productInfoMap.put(product.getId(), ProductInfo.from(product));
        }

        List<RankedProductInfo> rankedProducts = new ArrayList<>();
        for (ProductRankMonthlyModel rank : rankPage.getContent()) {
            ProductInfo productInfo = productInfoMap.get(rank.getProductId());
            if (productInfo != null) {
                rankedProducts.add(new RankedProductInfo(
                    rank.getRankNumber(),
                    rank.getScore(),
                    productInfo.withRanking((long) rank.getRankNumber())
                ));
            }
        }

        return new RankingPageInfo(
            targetDate,
            safePage,
            safeSize,
            rankPage.getTotalElements(),
            rankPage.getTotalPages(),
            rankedProducts
        );
    }

    public Long getTodayRank(Long productId) {
        if (productId == null || productId <= 0L) {
            return null;
        }

        String key = rankingKeyGenerator.dailyKey(rankingKeyGenerator.today());
        Long rank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
        return rank == null ? null : rank + 1;
    }

    private RankingResult fetchRanking(String key, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        long start = (long) (safePage - 1) * safeSize;
        long end = start + safeSize - 1;

        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        Long total = redisTemplate.opsForZSet().size(key);
        long totalElements = total == null ? 0L : total;

        if (tuples == null || tuples.isEmpty()) {
            return new RankingResult(safePage, safeSize, totalElements, totalPages(totalElements, safeSize), List.of());
        }

        List<Long> productIds = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple == null || tuple.getValue() == null) {
                continue;
            }
            productIds.add(Long.parseLong(tuple.getValue()));
        }

        Map<Long, ProductInfo> productInfoMap = new LinkedHashMap<>();
        for (ProductModel product : productService.getProductsByIds(productIds)) {
            productInfoMap.put(product.getId(), ProductInfo.from(product));
        }

        List<RankedProductInfo> rankedProducts = new ArrayList<>();
        int index = 0;
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple == null || tuple.getValue() == null) {
                index++;
                continue;
            }
            long productId = Long.parseLong(tuple.getValue());
            long rank = start + index + 1;
            ProductInfo productInfo = productInfoMap.get(productId);
            if (productInfo != null) {
                rankedProducts.add(new RankedProductInfo(
                    rank,
                    tuple.getScore() == null ? 0D : tuple.getScore(),
                    productInfo.withRanking(rank)
                ));
            }
            index++;
        }

        return new RankingResult(safePage, safeSize, totalElements, totalPages(totalElements, safeSize), rankedProducts);
    }

    private int totalPages(long totalElements, int size) {
        if (totalElements <= 0L) {
            return 0;
        }
        return (int) ((totalElements + size - 1) / size);
    }

    private String toYearWeek(LocalDate date) {
        int year = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", year, week);
    }

    private record RankingResult(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<RankedProductInfo> content
    ) {
    }
}
