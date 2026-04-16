package com.loopers.application.ranking;

import com.loopers.domain.product.Brand;
import com.loopers.domain.product.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.ProductRankEntry;
import com.loopers.domain.ranking.RankingRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RankingFacade {

    private static final DateTimeFormatter KEY_DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final String DEFAULT_REDIS_TEMPLATE = "defaultRedisTemplate";

    private final RedisTemplate<String, String> redisTemplate;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final RankingRepository rankingRepository;

    public RankingFacade(
        @Qualifier(DEFAULT_REDIS_TEMPLATE) RedisTemplate<String, String> redisTemplate,
        ProductRepository productRepository,
        BrandRepository brandRepository,
        RankingRepository rankingRepository
    ) {
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.rankingRepository = rankingRepository;
    }

    /**
     * 일간 Top-N 랭킹 조회 (Redis ZSET)
     */
    public List<RankingInfo> getTopRankings(String date, int page, int size) {
        String key = rankingKey(date);
        long start = (long) (page - 1) * size;
        long end = start + size - 1;

        Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (tuples == null || tuples.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> productIds = tuples.stream()
            .map(t -> Long.parseLong(t.getValue()))
            .toList();

        Map<Long, Product> productMap = fetchProductMap(productIds);
        Map<Long, Brand> brandMap = fetchBrandMap(productMap);

        long rank = start + 1;
        List<RankingInfo> result = new ArrayList<>();
        for (TypedTuple<String> tuple : tuples) {
            Long productId = Long.parseLong(tuple.getValue());
            Product product = productMap.get(productId);
            if (product == null) {
                rank++;
                continue;
            }
            Brand brand = product.getBrandId() != null ? brandMap.get(product.getBrandId()) : null;
            result.add(new RankingInfo(
                rank++,
                productId,
                product.getName(),
                product.getPrice(),
                brand != null ? brand.getName() : null,
                tuple.getScore() != null ? tuple.getScore() : 0
            ));
        }
        return result;
    }

    /**
     * 주간 랭킹 조회 (MV 테이블)
     */
    public List<RankingInfo> getWeeklyRankings(LocalDate date, int page, int size) {
        LocalDate weekStart = date.with(DayOfWeek.MONDAY);
        List<ProductRankEntry> entries = rankingRepository.findWeeklyRankings(weekStart, page, size);
        return enrichRankEntries(entries);
    }

    /**
     * 월간 랭킹 조회 (MV 테이블)
     */
    public List<RankingInfo> getMonthlyRankings(LocalDate date, int page, int size) {
        LocalDate monthStart = date.withDayOfMonth(1);
        List<ProductRankEntry> entries = rankingRepository.findMonthlyRankings(monthStart, page, size);
        return enrichRankEntries(entries);
    }

    /**
     * 개별 상품 순위 조회 (ZREVRANK, 0-based → 1-based 변환)
     */
    public Long getRank(Long productId) {
        String key = rankingKey(LocalDate.now(ZONE).format(KEY_DATE_FMT));
        Long rank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
        return rank != null ? rank + 1 : null;
    }

    private List<RankingInfo> enrichRankEntries(List<ProductRankEntry> entries) {
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> productIds = entries.stream().map(ProductRankEntry::productId).toList();
        Map<Long, Product> productMap = fetchProductMap(productIds);
        Map<Long, Brand> brandMap = fetchBrandMap(productMap);

        List<RankingInfo> result = new ArrayList<>();
        for (ProductRankEntry entry : entries) {
            Product product = productMap.get(entry.productId());
            if (product == null) continue;
            Brand brand = product.getBrandId() != null ? brandMap.get(product.getBrandId()) : null;
            result.add(new RankingInfo(
                entry.rankPosition(),
                entry.productId(),
                product.getName(),
                product.getPrice(),
                brand != null ? brand.getName() : null,
                entry.totalScore()
            ));
        }
        return result;
    }

    private Map<Long, Product> fetchProductMap(List<Long> productIds) {
        return productRepository.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));
    }

    private Map<Long, Brand> fetchBrandMap(Map<Long, Product> productMap) {
        List<Long> brandIds = productMap.values().stream()
            .map(Product::getBrandId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        if (brandIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return brandRepository.findAllByIds(brandIds).stream()
            .collect(Collectors.toMap(Brand::getId, b -> b));
    }

    private String rankingKey(String date) {
        return "ranking:all:" + date;
    }
}
