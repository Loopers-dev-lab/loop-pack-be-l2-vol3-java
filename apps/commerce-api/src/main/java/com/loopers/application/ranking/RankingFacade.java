package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.config.RankingProperties;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStatus;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyEntity;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyEntity;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import com.loopers.infrastructure.ranking.RankingRedisRepository.RankingEntry;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 랭킹 조회 Facade
 *
 * 네 가지 period를 지원한다:
 * - daily:   Redis ZSET  ranking:all:yyyyMMdd       (일간 인기 상품)
 * - hourly:  Redis ZSET  ranking:hourly:yyyyMMddHH  (지금 뜨는 상품)
 * - weekly:  MV 테이블   mv_product_rank_weekly     (주간 TOP 100, batch 집계)
 * - monthly: MV 테이블   mv_product_rank_monthly    (월간 TOP 100, batch 집계)
 *
 * daily/hourly는 실시간 스트리밍 갱신, weekly/monthly는 매일 배치로 재계산.
 * 공통 응답 스키마 RankingPageResult를 사용한다.
 *
 * @Transactional 미사용:
 * - Redis/MV 조회는 TX 불필요
 * - ProductService, BrandService가 자체 @Transactional 관리
 */
@Component
public class RankingFacade {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAILY_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter HOURLY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final Set<ProductStatus> DISPLAYABLE_STATUSES = Set.of(
            ProductStatus.ACTIVE, ProductStatus.SOLDOUT
    );

    private final RankingRedisRepository rankingRedisRepository;
    private final MvProductRankWeeklyJpaRepository mvWeeklyRepository;
    private final MvProductRankMonthlyJpaRepository mvMonthlyRepository;
    private final ProductService productService;
    private final BrandService brandService;
    private final RankingProperties rankingProperties;

    public RankingFacade(RankingRedisRepository rankingRedisRepository,
                          MvProductRankWeeklyJpaRepository mvWeeklyRepository,
                          MvProductRankMonthlyJpaRepository mvMonthlyRepository,
                          ProductService productService,
                          BrandService brandService,
                          RankingProperties rankingProperties) {
        this.rankingRedisRepository = rankingRedisRepository;
        this.mvWeeklyRepository = mvWeeklyRepository;
        this.mvMonthlyRepository = mvMonthlyRepository;
        this.productService = productService;
        this.brandService = brandService;
        this.rankingProperties = rankingProperties;
    }

    /**
     * 랭킹 페이지 조회.
     *
     * period에 따라 저장소가 분기된다:
     * - daily/hourly: Redis ZSET
     * - weekly/monthly: MySQL MV 테이블
     *
     * 공통적으로 비활성 상품(HIDDEN, DISCONTINUED)은 응답에서 제외된다.
     * 이로 인해 페이지 내 항목 수가 요청 size보다 적을 수 있다.
     *
     * @param period 조회 기간 ("daily" | "hourly" | "weekly" | "monthly"). null이면 daily
     * @param date   조회 날짜(yyyyMMdd) 또는 시간(yyyyMMddHH, hourly일 때). null이면 현재
     * @param page   페이지 번호 (1-based)
     * @param size   페이지 크기
     */
    public RankingPageResult getRankings(String period, String date, int page, int size) {
        if ("weekly".equalsIgnoreCase(period)) {
            return getWeeklyRankings(date, page, size);
        }
        if ("monthly".equalsIgnoreCase(period)) {
            return getMonthlyRankings(date, page, size);
        }
        return getRedisRankings(period, date, page, size);
    }

    /**
     * 특정 상품의 오늘 랭킹 정보를 조회한다.
     *
     * @return (1-based 순위, 점수). ZSET에 없으면 null.
     */
    public ProductRankInfo getProductRank(Long productId) {
        String key = buildKey(false, null);

        Long zeroBasedRank = rankingRedisRepository.getRank(key, productId);
        if (zeroBasedRank == null) {
            return null;
        }

        Double score = rankingRedisRepository.getScore(key, productId);
        return new ProductRankInfo(zeroBasedRank.intValue() + 1, score != null ? score : 0.0);
    }

    // ---------- Redis 경로 (daily / hourly) ----------

    private RankingPageResult getRedisRankings(String period, String date, int page, int size) {
        boolean isHourly = "hourly".equalsIgnoreCase(period);
        String key = buildKey(isHourly, date);

        long totalCount = rankingRedisRepository.getSize(key);

        // 콜드 스타트 fallback: 키 비어있고 날짜 미지정이면 이전 윈도우로 시도
        if (totalCount == 0 && (date == null || date.isBlank())) {
            String fallbackKey = buildFallbackKey(isHourly);
            long fallbackCount = rankingRedisRepository.getSize(fallbackKey);
            if (fallbackCount > 0) {
                key = fallbackKey;
                totalCount = fallbackCount;
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

        List<Long> productIds = entries.stream().map(RankingEntry::productId).toList();

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

    // ---------- MV 경로 (weekly / monthly) ----------

    private RankingPageResult getWeeklyRankings(String date, int page, int size) {
        LocalDate targetDate = parseDateOrToday(date);
        String yearWeek = String.format("%d-W%02d",
                targetDate.get(IsoFields.WEEK_BASED_YEAR),
                targetDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));

        int fromRank = (page - 1) * size + 1;
        int toRank = page * size;

        List<MvProductRankWeeklyEntity> rows = mvWeeklyRepository
                .findByYearWeekAndRankNoBetweenOrderByRankNoAsc(yearWeek, fromRank, toRank);
        if (rows.isEmpty()) {
            return RankingPageResult.empty(page, size);
        }

        List<Long> productIds = rows.stream()
                .map(MvProductRankWeeklyEntity::getProductId)
                .toList();

        Map<Long, ProductInfo> productMap = loadDisplayableProductMap(productIds);
        Map<Long, String> brandNameMap = loadBrandNameMap(productMap);

        List<RankingItemInfo> items = new ArrayList<>();
        for (MvProductRankWeeklyEntity row : rows) {
            ProductInfo product = productMap.get(row.getProductId());
            if (product != null) {
                String brandName = brandNameMap.get(product.brandId());
                items.add(new RankingItemInfo(row.getRankNo(), product, brandName, row.getScore()));
            }
        }

        long totalCount = mvWeeklyRepository.countByYearWeek(yearWeek);
        boolean hasNext = (long) page * size < totalCount;
        return new RankingPageResult(items, page, size, totalCount, hasNext);
    }

    private RankingPageResult getMonthlyRankings(String date, int page, int size) {
        LocalDate targetDate = parseDateOrToday(date);
        String periodMonth = String.format("%d-%02d",
                targetDate.getYear(), targetDate.getMonthValue());

        int fromRank = (page - 1) * size + 1;
        int toRank = page * size;

        List<MvProductRankMonthlyEntity> rows = mvMonthlyRepository
                .findByPeriodMonthAndRankNoBetweenOrderByRankNoAsc(periodMonth, fromRank, toRank);
        if (rows.isEmpty()) {
            return RankingPageResult.empty(page, size);
        }

        List<Long> productIds = rows.stream()
                .map(MvProductRankMonthlyEntity::getProductId)
                .toList();

        Map<Long, ProductInfo> productMap = loadDisplayableProductMap(productIds);
        Map<Long, String> brandNameMap = loadBrandNameMap(productMap);

        List<RankingItemInfo> items = new ArrayList<>();
        for (MvProductRankMonthlyEntity row : rows) {
            ProductInfo product = productMap.get(row.getProductId());
            if (product != null) {
                String brandName = brandNameMap.get(product.brandId());
                items.add(new RankingItemInfo(row.getRankNo(), product, brandName, row.getScore()));
            }
        }

        long totalCount = mvMonthlyRepository.countByPeriodMonth(periodMonth);
        boolean hasNext = (long) page * size < totalCount;
        return new RankingPageResult(items, page, size, totalCount, hasNext);
    }

    // ---------- 공통 헬퍼 ----------

    private LocalDate parseDateOrToday(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now(KST);
        }
        return LocalDate.parse(date, DAILY_FORMAT);
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

    /**
     * Redis period(daily/hourly)의 ZSET 키를 생성한다.
     */
    private String buildKey(boolean isHourly, String date) {
        if (isHourly) {
            if (date == null || date.isBlank()) {
                date = LocalDateTime.now(KST).format(HOURLY_FORMAT);
            }
            return rankingProperties.getHourlyKeyPrefix() + ":" + date;
        } else {
            if (date == null || date.isBlank()) {
                date = LocalDate.now(KST).format(DAILY_FORMAT);
            }
            return rankingProperties.getKeyPrefix() + ":" + date;
        }
    }

    /**
     * 콜드 스타트 fallback 키: daily → 어제, hourly → 직전 시간
     */
    private String buildFallbackKey(boolean isHourly) {
        if (isHourly) {
            String prevHour = LocalDateTime.now(KST).minusHours(1).format(HOURLY_FORMAT);
            return rankingProperties.getHourlyKeyPrefix() + ":" + prevHour;
        } else {
            String yesterday = LocalDate.now(KST).minusDays(1).format(DAILY_FORMAT);
            return rankingProperties.getKeyPrefix() + ":" + yesterday;
        }
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
