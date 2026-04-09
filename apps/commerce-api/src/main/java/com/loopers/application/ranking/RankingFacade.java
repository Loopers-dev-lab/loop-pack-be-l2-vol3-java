package com.loopers.application.ranking;

import com.loopers.domain.product.Brand;
import com.loopers.domain.product.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RankingFacade {

    private static final DateTimeFormatter KEY_DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String DEFAULT_REDIS_TEMPLATE = "defaultRedisTemplate";

    private final RedisTemplate<String, String> redisTemplate;
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;

    public RankingFacade(
        @Qualifier(DEFAULT_REDIS_TEMPLATE) RedisTemplate<String, String> redisTemplate,
        ProductRepository productRepository,
        BrandRepository brandRepository
    ) {
        this.redisTemplate = redisTemplate;
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
    }

    /**
     * Top-N 랭킹 조회 (ZREVRANGE + 상품 정보 Aggregation)
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

        Map<Long, Product> productMap = productRepository.findAllByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        Map<Long, Brand> brandMap = productMap.values().stream()
            .map(Product::getBrandId)
            .filter(id -> id != null)
            .distinct()
            .flatMap(id -> brandRepository.findById(id).stream())
            .collect(Collectors.toMap(Brand::getId, b -> b));

        long rank = start + 1;
        List<RankingInfo> result = new java.util.ArrayList<>();
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
     * 개별 상품 순위 조회 (ZREVRANK, 0-based → 1-based 변환)
     */
    public Long getRank(Long productId) {
        String key = rankingKey(LocalDate.now().format(KEY_DATE_FMT));
        Long rank = redisTemplate.opsForZSet().reverseRank(key, String.valueOf(productId));
        return rank != null ? rank + 1 : null;
    }

    private String rankingKey(String date) {
        return "ranking:all:" + date;
    }
}
