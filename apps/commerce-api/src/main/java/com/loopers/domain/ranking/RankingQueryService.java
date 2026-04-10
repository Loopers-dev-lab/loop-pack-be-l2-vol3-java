package com.loopers.domain.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortOrder;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * 일간 랭킹 조회: Redis ZSET에서 <strong>상품 ID·score</strong>만 가져온 뒤, 상품·브랜드·좋아요 집계를 조합한다(Hydration).
 * <p>
 * Redis 장애 시 §4.2에 따라 DB 최신 등록순 fallback·메트릭·구조화 로그(MDC)를 적용할 수 있다.
 */
@Service
public class RankingQueryService {

    private static final Logger log = LoggerFactory.getLogger(RankingQueryService.class);

    private static final String MDC_RANKING_DEGRADED = "ranking.degraded";
    private static final String MDC_RANKING_OPERATION = "ranking.operation";

    private final RankingReadRepository rankingReadRepository;
    private final ProductRepository productRepository;
    private final BrandService brandService;
    private final LikeService likeService;
    private final MeterRegistry meterRegistry;
    private final boolean fallbackOnRedisFailure;

    public RankingQueryService(
            RankingReadRepository rankingReadRepository,
            ProductRepository productRepository,
            BrandService brandService,
            LikeService likeService,
            MeterRegistry meterRegistry,
            @Value("${app.ranking.fallback-on-redis-failure:true}") boolean fallbackOnRedisFailure) {
        this.rankingReadRepository = rankingReadRepository;
        this.productRepository = productRepository;
        this.brandService = brandService;
        this.likeService = likeService;
        this.meterRegistry = meterRegistry;
        this.fallbackOnRedisFailure = fallbackOnRedisFailure;
    }

    /**
     * 일간 랭킹 페이지 조회
     *
     * @param rankingDate 일간 랭킹 일자
     * @param pageOneBased 페이지 (1부터)
     * @param size 페이지 크기
     * @return 일간 랭킹 페이지 결과
     */
    public RankingPage loadPage(LocalDate rankingDate, int pageOneBased, int size) {
        if (pageOneBased < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 1 이상이어야 합니다.");
        }
        if (size < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1 이상이어야 합니다.");
        }
        String key = RankingKey.dailyAll(rankingDate);
        long total;
        try {
            total = rankingReadRepository.count(key);
        } catch (RedisConnectionFailureException | RedisSystemException ex) {
            return onRedisCountFailure(key, "loadPage.count", ex, pageOneBased, size);
        }
        int totalPages = computeTotalPages(total, size);
        if (total == 0L) {
            return new RankingPage(List.of(), pageOneBased, size, 0L, 0, RankingListSource.REDIS_ZSET);
        }
        long startIndex = (long) (pageOneBased - 1) * size;
        if (startIndex >= total) {
            return new RankingPage(List.of(), pageOneBased, size, total, totalPages, RankingListSource.REDIS_ZSET);
        }
        long endIndex = Math.min(startIndex + size - 1, total - 1);
        List<RankingZsetEntry> entries;
        try {
            entries = rankingReadRepository.findReverseRangeWithScores(key, startIndex, endIndex);
        } catch (RedisConnectionFailureException | RedisSystemException ex) {
            return onRedisCountFailure(key, "loadPage.zrevrange", ex, pageOneBased, size);
        }
        List<Long> parsedIds = new ArrayList<>();
        for (RankingZsetEntry e : entries) {
            try {
                parsedIds.add(Long.parseLong(e.member()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (parsedIds.isEmpty()) {
            return new RankingPage(List.of(), pageOneBased, size, total, totalPages, RankingListSource.REDIS_ZSET);
        }
        Map<Long, ProductModel> productMap = productRepository.findByIdInAndNotDeletedAsMap(parsedIds);

        List<Long> brandIds = productMap.values().stream()
                .map(ProductModel::getBrandId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, BrandModel> brandMap = brandIds.isEmpty()
                ? Map.of()
                : brandService.findByIdAndNotDeletedIn(brandIds);
        Map<Long, Long> likeMap = likeService.getLikeCountByProductIdsFromStats(parsedIds);

        List<RankingRow> rows = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            RankingZsetEntry e = entries.get(i);
            long productId;
            try {
                productId = Long.parseLong(e.member());
            } catch (NumberFormatException ex) {
                continue;
            }
            ProductModel product = productMap.get(productId);
            if (product == null) {
                continue;
            }
            BrandModel brand = brandMap.get(product.getBrandId());
            if (brand == null) {
                continue;
            }
            int rank = (int) (startIndex + i + 1);
            long likeCount = likeMap.getOrDefault(productId, 0L);
            rows.add(new RankingRow(
                    rank,
                    productId,
                    e.score(),
                    product.getName(),
                    product.getPrice(),
                    product.getBrandId(),
                    brand.getName(),
                    likeCount,
                    product.getStockQuantity()
            ));
        }
        return new RankingPage(rows, pageOneBased, size, total, totalPages, RankingListSource.REDIS_ZSET);
    }

    /**
     * Redis 불가 시 미삭제 상품을 최신 등록순으로 채운다(§4.2). score는 ZSET이 없으므로 0으로 둔다.
     * @param key Redis 키
     * @param operation 작업 이름
     * @param ex 예외
     * @param pageOneBased 페이지 (1부터)
     * @param size 페이지 크기
     * @return 랭킹 페이지 결과
     */
    private RankingPage onRedisCountFailure(
            String key,
            String operation,
            Exception ex,
            int pageOneBased,
            int size) {
        degradedCounter(operation).increment();
        try {
            // MDC 설정
            MDC.put(MDC_RANKING_DEGRADED, "true");
            MDC.put(MDC_RANKING_OPERATION, operation);
            log.warn("ranking.degraded=true operation={} key={} errorClass={} message={}",
                    operation, key, ex.getClass().getSimpleName(), ex.getMessage());
            if (!fallbackOnRedisFailure) {
                return new RankingPage(List.of(), pageOneBased, size, 0L, 0, RankingListSource.DEGRADED_EMPTY);
            }
            return loadPageFromLatestProducts(pageOneBased, size);
        } finally {
            MDC.remove(MDC_RANKING_DEGRADED);
            MDC.remove(MDC_RANKING_OPERATION);
        }
    }

    /**
     * Redis 불가 시 미삭제 상품을 최신 등록순으로 채운다(§4.2). score는 ZSET이 없으므로 0으로 둔다.
     */
    private RankingPage loadPageFromLatestProducts(int pageOneBased, int size) {
        Pageable pageable = PageRequest.of(pageOneBased - 1, size);
        Page<ProductModel> productPage = productRepository.findNotDeleted(ProductSortOrder.LATEST, null, pageable);
        List<ProductModel> content = productPage.getContent();
        if (content.isEmpty()) {
            return new RankingPage(
                    List.of(),
                    pageOneBased,
                    size,
                    productPage.getTotalElements(),
                    productPage.getTotalPages(),
                    RankingListSource.DEGRADED_EMPTY
            );
        }
        List<Long> ids = content.stream().map(ProductModel::getId).toList();
        List<Long> brandIds = content.stream()
                .map(ProductModel::getBrandId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, BrandModel> brandMap = brandIds.isEmpty()
                ? Map.of()
                : brandService.findByIdAndNotDeletedIn(brandIds);
        Map<Long, Long> likeMap = likeService.getLikeCountByProductIdsFromStats(ids);

        int startRank = (pageOneBased - 1) * size + 1;
        List<RankingRow> rows = new ArrayList<>();
        for (int i = 0; i < content.size(); i++) {
            ProductModel product = content.get(i);
            BrandModel brand = brandMap.get(product.getBrandId());
            if (brand == null) {
                continue;
            }
            long likeCount = likeMap.getOrDefault(product.getId(), 0L);
            rows.add(new RankingRow(
                    startRank + i,
                    product.getId(),
                    0.0d,
                    product.getName(),
                    product.getPrice(),
                    product.getBrandId(),
                    brand.getName(),
                    likeCount,
                    product.getStockQuantity()
            ));
        }
        return new RankingPage(
                rows,
                pageOneBased,
                size,
                productPage.getTotalElements(),
                productPage.getTotalPages(),
                RankingListSource.FALLBACK_DB_LATEST
        );
    }

    private Counter degradedCounter(String operation) {
        return Counter.builder("ranking.degraded")
                .tag("operation", operation)
                .register(meterRegistry);
    }

    /**
     * 일간 랭킹 ZSET에서 상품의 전역 순위(1-based, 점수 내림차순)를 조회한다.
     *
     * @param rankingDate 랭킹 일자
     * @param productId   상품 ID (양수)
     * @return 순위가 있으면 값, ZSET 미등록 시 empty
     * @throws CoreException {@code productId <= 0}
     */
    public OptionalLong findOneBasedDailyRank(LocalDate rankingDate, long productId) {
        if (productId <= 0L) {
            throw new CoreException(ErrorType.BAD_REQUEST, "productId must be positive");
        }
        String key = RankingKey.dailyAll(rankingDate);
        String member = String.valueOf(productId);
        try {
            return rankingReadRepository.findOneBasedReverseRank(key, member);
        } catch (RedisConnectionFailureException | RedisSystemException ex) {
            degradedCounter("findOneBasedDailyRank").increment();
            // MDC 설정
            try {
                MDC.put(MDC_RANKING_DEGRADED, "true");
                MDC.put(MDC_RANKING_OPERATION, "findOneBasedDailyRank");
                log.warn("ranking.degraded=true operation=findOneBasedDailyRank key={} member={} errorClass={} message={}",
                        key, member, ex.getClass().getSimpleName(), ex.getMessage());
            } finally {
                MDC.remove(MDC_RANKING_DEGRADED);
                MDC.remove(MDC_RANKING_OPERATION);
            }
            return OptionalLong.empty();
        }
    }

    private static int computeTotalPages(long total, int size) {
        if (total <= 0L || size <= 0) {
            return 0;
        }
        return (int) ((total + size - 1) / size);
    }
}
