package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.config.RankingProperties;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStatus;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import com.loopers.infrastructure.ranking.RankingRedisRepository.RankingEntry;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 랭킹 조회 Facade
 *
 * Redis ZSET 랭킹 데이터 + Product + Brand를 조합하여 랭킹 API 응답을 구성한다.
 *
 * @Transactional 미사용:
 * - Redis 조회는 TX 불필요
 * - ProductService, BrandService가 자체 @Transactional 관리
 * - ProductFacade와 동일한 패턴
 */
@Component
public class RankingFacade {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Set<ProductStatus> DISPLAYABLE_STATUSES = Set.of(
            ProductStatus.ACTIVE, ProductStatus.SOLDOUT
    );

    private final RankingRedisRepository rankingRedisRepository;
    private final ProductService productService;
    private final BrandService brandService;
    private final RankingProperties rankingProperties;

    public RankingFacade(RankingRedisRepository rankingRedisRepository,
                          ProductService productService,
                          BrandService brandService,
                          RankingProperties rankingProperties) {
        this.rankingRedisRepository = rankingRedisRepository;
        this.productService = productService;
        this.brandService = brandService;
        this.rankingProperties = rankingProperties;
    }

    /**
     * 랭킹 페이지 조회
     *
     * ZSET에서 offset 기반으로 상위 N개를 조회한 뒤,
     * 상품/브랜드 정보를 일괄 로드하여 조합한다.
     *
     * 비활성 상품(HIDDEN, DISCONTINUED)은 응답에서 제외된다.
     * 이로 인해 페이지 내 항목 수가 요청 size보다 적을 수 있다.
     *
     * @param date 조회 날짜 (yyyyMMdd). null이면 오늘(KST)
     * @param page 페이지 번호 (1-based)
     * @param size 페이지 크기
     */
    public RankingPageResult getRankings(String date, int page, int size) {
        String key = buildKey(date);

        long totalCount = rankingRedisRepository.getSize(key);

        // 콜드 스타트 fallback: 오늘 키가 비어있고 날짜 미지정(오늘)이면 어제로 시도
        if (totalCount == 0 && (date == null || date.isBlank())) {
            String yesterdayKey = buildKey(
                    LocalDate.now(KST).minusDays(1).format(DATE_FORMAT));
            long yesterdayCount = rankingRedisRepository.getSize(yesterdayKey);
            if (yesterdayCount > 0) {
                key = yesterdayKey;
                totalCount = yesterdayCount;
            }
        }

        if (totalCount == 0) {
            return RankingPageResult.empty(page, size);
        }

        long start = (long) (page - 1) * size;
        long end = start + size - 1;

        List<RankingEntry> entries = rankingRedisRepository.getTopWithScores(key, start, end);
        if (entries.isEmpty()) {
            return RankingPageResult.empty(page, size);
        }

        List<Long> productIds = entries.stream()
                .map(RankingEntry::productId)
                .toList();

        Map<Long, ProductInfo> productMap = loadDisplayableProductMap(productIds);
        Map<Long, String> brandNameMap = loadBrandNameMap(productMap);

        List<RankingItemInfo> items = new ArrayList<>();
        int rank = (int) start + 1;
        for (RankingEntry entry : entries) {
            ProductInfo product = productMap.get(entry.productId());
            if (product != null) {
                String brandName = brandNameMap.get(product.brandId());
                items.add(new RankingItemInfo(rank, product, brandName, entry.score()));
            }
            rank++;
        }

        boolean hasNext = start + size < totalCount;
        return new RankingPageResult(items, page, size, totalCount, hasNext);
    }

    /**
     * 특정 상품의 오늘 랭킹 정보를 조회한다.
     *
     * @return (1-based 순위, 점수). ZSET에 없으면 null.
     */
    public ProductRankInfo getProductRank(Long productId) {
        String key = buildKey(null);

        Long zeroBasedRank = rankingRedisRepository.getRank(key, productId);
        if (zeroBasedRank == null) {
            return null;
        }

        Double score = rankingRedisRepository.getScore(key, productId);
        return new ProductRankInfo(zeroBasedRank.intValue() + 1, score != null ? score : 0.0);
    }

    private Map<Long, ProductInfo> loadDisplayableProductMap(List<Long> productIds) {
        List<Product> products = productService.getProductsByIds(productIds);
        return products.stream()
                .filter(p -> DISPLAYABLE_STATUSES.contains(p.getStatus()))
                .collect(Collectors.toMap(Product::getId, ProductInfo::from));
    }

    private Map<Long, String> loadBrandNameMap(Map<Long, ProductInfo> productMap) {
        List<Long> brandIds = productMap.values().stream()
                .map(ProductInfo::brandId)
                .distinct()
                .toList();

        if (brandIds.isEmpty()) {
            return Map.of();
        }

        return brandService.getBrandsByIds(brandIds).stream()
                .collect(Collectors.toMap(Brand::getId, Brand::getName));
    }

    private String buildKey(String date) {
        if (date == null || date.isBlank()) {
            date = LocalDate.now(KST).format(DATE_FORMAT);
        }
        return rankingProperties.getKeyPrefix() + ":" + date;
    }

    public record RankingPageResult(
            List<RankingItemInfo> items,
            int page,
            int size,
            long totalCount,
            boolean hasNext
    ) {
        public static RankingPageResult empty(int page, int size) {
            return new RankingPageResult(List.of(), page, size, 0, false);
        }
    }

    public record ProductRankInfo(int rank, double score) {}
}
