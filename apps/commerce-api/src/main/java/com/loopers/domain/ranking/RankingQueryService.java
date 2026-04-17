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

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * 일간 랭킹 조회: Redis ZSET에서 <strong>상품 ID·score</strong>만 가져온 뒤, 상품·브랜드·좋아요 집계를 조합한다(Hydration).
 * <p>
 * Redis 장애 시 §4.2에 따라 DB 최신 등록순 fallback·메트릭·구조화 로그(MDC)를 적용할 수 있다.
 * 스냅샷 키 조회 시에는 DB fallback을 쓰지 않는다(스냅샷 의미가 깨짐).
 */
@Service
public class RankingQueryService {

    private static final Logger log = LoggerFactory.getLogger(RankingQueryService.class);

    private static final String MDC_RANKING_DEGRADED = "ranking.degraded";
    private static final String MDC_RANKING_OPERATION = "ranking.operation";

    private final RankingReadRepository rankingReadRepository;
    private final RankingSnapshotRepository rankingSnapshotRepository;
    private final RankingMvReadRepository rankingMvReadRepository;
    private final ProductRepository productRepository;
    private final BrandService brandService;
    private final LikeService likeService;
    private final MeterRegistry meterRegistry;
    private final boolean fallbackOnRedisFailure;
    private final long snapshotTtlSeconds;

    public RankingQueryService(
            RankingReadRepository rankingReadRepository,
            RankingSnapshotRepository rankingSnapshotRepository,
            RankingMvReadRepository rankingMvReadRepository,
            ProductRepository productRepository,
            BrandService brandService,
            LikeService likeService,
            MeterRegistry meterRegistry,
            @Value("${app.ranking.fallback-on-redis-failure:true}") boolean fallbackOnRedisFailure,
            @Value("${app.ranking.snapshot-ttl-seconds:600}") long snapshotTtlSeconds) {
        this.rankingReadRepository = rankingReadRepository;
        this.rankingSnapshotRepository = rankingSnapshotRepository;
        this.rankingMvReadRepository = rankingMvReadRepository;
        this.productRepository = productRepository;
        this.brandService = brandService;
        this.likeService = likeService;
        this.meterRegistry = meterRegistry;
        this.fallbackOnRedisFailure = fallbackOnRedisFailure;
        this.snapshotTtlSeconds = snapshotTtlSeconds;
    }

    /**
     * 주간/월간 MV에서 랭킹 페이지를 조회한다. 행 수는 최대 100으로 캡한다.
     *
     * @param period        WEEKLY 또는 MONTHLY
     * @param periodKey     검증된 기간 키(주간 yyyyWww, 월간 yyyyMM)
     * @param pageOneBased  페이지 (1부터)
     * @param size          페이지 크기
     * @return MV 기준 랭킹 페이지
     */
    public RankingPage loadMvPage(
            RankingMvPeriod period,
            String periodKey,
            int pageOneBased,
            int size) {
        validatePageAndSize(pageOneBased, size);
        RankingListSource listSource = switch (period) {
            case WEEKLY -> RankingListSource.MV_WEEKLY;
            case MONTHLY -> RankingListSource.MV_MONTHLY;
        };
        Optional<Integer> maxVersion = switch (period) {
            case WEEKLY -> rankingMvReadRepository.findMaxVersionForWeekly(periodKey);
            case MONTHLY -> rankingMvReadRepository.findMaxVersionForMonthly(periodKey);
        };
        if (maxVersion.isEmpty()) {
            return new RankingPage(
                    List.of(), pageOneBased, size, 0L, 0, listSource, null, null);
        }
        int activeVersion = maxVersion.get();
        List<RankingMvTableRow> all = switch (period) {
            case WEEKLY -> rankingMvReadRepository.findWeeklyByPeriodKeyAndVersionOrdered(
                    periodKey, activeVersion);
            case MONTHLY -> rankingMvReadRepository.findMonthlyByPeriodKeyAndVersionOrdered(
                    periodKey, activeVersion);
        };
        List<RankingMvTableRow> capped = all.stream().limit(100).toList();
        long total = capped.size();
        int totalPages = computeTotalPages(total, size);
        if (total == 0L) {
            return new RankingPage(
                    List.of(), pageOneBased, size, 0L, 0, listSource, null, activeVersion);
        }
        long startIndex = (long) (pageOneBased - 1) * size;
        if (startIndex >= total) {
            meterRegistry.counter("ranking.mv.page_beyond", "period", period.name()).increment();
            log.debug(
                    "ranking.mv.page_beyond period={} periodKey={} total={} page={} size={}",
                    period,
                    periodKey,
                    total,
                    pageOneBased,
                    size);
            return new RankingPage(
                    List.of(), pageOneBased, size, total, totalPages, listSource, null, activeVersion);
        }
        int from = (int) startIndex;
        int to = (int) Math.min(startIndex + size, total);
        List<RankingMvTableRow> slice = capped.subList(from, to);
        List<RankingRow> rows = hydrateMvRows(slice);
        return new RankingPage(
                rows, pageOneBased, size, total, totalPages, listSource, null, activeVersion);
    }

    /**
     * 주간/월간 MV 행을 랭킹 행으로 변환한다.
     *
     * @param slice 주간/월간 MV 행
     * @return 랭킹 행
     */
    private List<RankingRow> hydrateMvRows(List<RankingMvTableRow> slice) {
        if (slice.isEmpty()) {
            return List.of();
        }
        List<Long> parsedIds = slice.stream().map(RankingMvTableRow::productId).toList();
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
        for (RankingMvTableRow row : slice) {
            ProductModel product = productMap.get(row.productId());
            if (product == null) {
                continue;
            }
            BrandModel brand = brandMap.get(product.getBrandId());
            if (brand == null) {
                continue;
            }
            long likeCount = likeMap.getOrDefault(row.productId(), 0L);
            rows.add(new RankingRow(
                    row.rankValue(),
                    row.productId(),
                    row.score().doubleValue(),
                    product.getName(),
                    product.getPrice(),
                    product.getBrandId(),
                    brand.getName(),
                    likeCount,
                    product.getStockQuantity()
            ));
        }
        return rows;
    }

    /**
     * 일간 랭킹 페이지 조회
     * @param rankingDate 랭킹 일자
     * @param pageOneBased 페이지 (1부터)
     * @param size 페이지 크기
     * @return 랭킹 페이지 결과
     */
    public RankingPage loadPage(LocalDate rankingDate, int pageOneBased, int size) {
        return loadPage(rankingDate, pageOneBased, size, Optional.empty());
    }

    /**
     * 일간 랭킹 페이지 조회. {@code rankingSnapshotId}가 있으면 해당 스냅샷 ZSET에서만 페이징한다.
     * @param rankingDate 랭킹 일자
     * @param pageOneBased 페이지 (1부터)
     * @param size 페이지 크기
     * @param rankingSnapshotIdRaw 스냅샷 ID
     * @return 랭킹 페이지 결과
     */
    public RankingPage loadPage(
            LocalDate rankingDate,
            int pageOneBased,
            int size,
            Optional<String> rankingSnapshotIdRaw) {
        validatePageAndSize(pageOneBased, size);
        String dailyKey = RankingKey.dailyAll(rankingDate);
        final String zsetKey;
        final RankingListSource listSource;
        final String rankingSnapshotIdEcho;
        final boolean allowDbFallback;
        if (rankingSnapshotIdRaw.isEmpty() || rankingSnapshotIdRaw.get().isBlank()) {
            zsetKey = dailyKey;
            listSource = RankingListSource.REDIS_ZSET;
            rankingSnapshotIdEcho = null;
            allowDbFallback = true;
        } else {
            String sid = normalizeRankingSnapshotId(rankingSnapshotIdRaw.get());
            zsetKey = RankingKey.snapshot(rankingDate, sid);
            listSource = RankingListSource.REDIS_ZSET_SNAPSHOT;
            rankingSnapshotIdEcho = sid;
            allowDbFallback = false;
            if (!rankingSnapshotRepository.exists(zsetKey)) {
                throw new CoreException(ErrorType.NOT_FOUND, "rankingSnapshotId가 없거나 만료되었습니다.");
            }
        }
        return loadPageFromZsetKey(
                zsetKey, pageOneBased, size, listSource, rankingSnapshotIdEcho, allowDbFallback);
    }

    /**
     * 일간 ZSET을 스냅샷 키로 복제하고 식별자를 반환한다.
     * @param rankingDate 랭킹 일자
     * @return 랭킹 스냅샷 생성 결과    
     */
    public RankingSnapshotCreateResult createSnapshot(LocalDate rankingDate) {
        String dailyKey = RankingKey.dailyAll(rankingDate);
        String snapshotId = UUID.randomUUID().toString();
        String snapshotKey = RankingKey.snapshot(rankingDate, snapshotId);
        try {
            long total = rankingSnapshotRepository.materialize(
                    dailyKey, snapshotKey, Duration.ofSeconds(snapshotTtlSeconds));
            return new RankingSnapshotCreateResult(snapshotId, total, snapshotTtlSeconds);
        } catch (RedisConnectionFailureException | RedisSystemException ex) {
            degradedCounter("createSnapshot").increment();
            try {
                MDC.put(MDC_RANKING_DEGRADED, "true");
                MDC.put(MDC_RANKING_OPERATION, "createSnapshot");
                log.warn("ranking.degraded=true operation=createSnapshot key={} errorClass={} message={}",
                        snapshotKey, ex.getClass().getSimpleName(), ex.getMessage());
            } finally {
                MDC.remove(MDC_RANKING_DEGRADED);
                MDC.remove(MDC_RANKING_OPERATION);
            }
            throw new CoreException(ErrorType.INTERNAL_ERROR, "랭킹 스냅샷을 만들 수 없습니다.");
        }
    }

    /**
     * 페이지와 페이지 크기를 검증한다.
     * @param pageOneBased 페이지 (1부터)
     * @param size 페이지 크기
     * @throws CoreException 페이지가 1 이상이 아니거나 페이지 크기가 1 이상이 아닌 경우
     */
    private static void validatePageAndSize(int pageOneBased, int size) {
        if (pageOneBased < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 1 이상이어야 합니다.");
        }
        if (size < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1 이상이어야 합니다.");
        }
    }

    /**
     * 랭킹 스냅샷 ID를 정규화한다.
     * @param raw 랭킹 스냅샷 ID
     * @return 정규화된 랭킹 스냅샷 ID
     * @throws CoreException 랭킹 스냅샷 ID가 비어 있거나 UUID 형식이 아닌 경우
     */
    private static String normalizeRankingSnapshotId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "rankingSnapshotId가 비어 있습니다.");
        }
        try {
            return UUID.fromString(raw.trim()).toString();
        } catch (IllegalArgumentException ex) {
            throw new CoreException(ErrorType.BAD_REQUEST, "rankingSnapshotId는 UUID 형식이어야 합니다.");
        }
    }

    /**
     * 랭킹 ZSET 키에서 페이지를 조회한다.
     * @param zsetKey 랭킹 ZSET 키
     * @param pageOneBased 페이지 (1부터)
     * @param size 페이지 크기
     * @param listSource 목록 생성 경로
     * @param rankingSnapshotIdEcho 스냅샷 조회 시 발급·요청한 UUID
     * @param allowDbFallbackOnRedisFailure Redis 장애 시 DB fallback 허용 여부
     * @return 랭킹 페이지 결과
     */
    private RankingPage loadPageFromZsetKey(
            String zsetKey,
            int pageOneBased,
            int size,
            RankingListSource listSource,
            String rankingSnapshotIdEcho,
            boolean allowDbFallbackOnRedisFailure) {
        long total;
        try {
            total = rankingReadRepository.count(zsetKey);
        } catch (RedisConnectionFailureException | RedisSystemException ex) {
            return onRedisFailure(
                    zsetKey, "loadPage.count", ex, pageOneBased, size, allowDbFallbackOnRedisFailure, rankingSnapshotIdEcho);
        }
        int totalPages = computeTotalPages(total, size);
        if (total == 0L) {
            return new RankingPage(
                    List.of(), pageOneBased, size, 0L, 0, listSource, rankingSnapshotIdEcho, null);
        }
        long startIndex = (long) (pageOneBased - 1) * size;
        if (startIndex >= total) {
            return new RankingPage(
                    List.of(), pageOneBased, size, total, totalPages, listSource, rankingSnapshotIdEcho, null);
        }
        long endIndex = Math.min(startIndex + size - 1, total - 1);
        List<RankingZsetEntry> entries;
        try {
            entries = rankingReadRepository.findReverseRangeWithScores(zsetKey, startIndex, endIndex);
        } catch (RedisConnectionFailureException | RedisSystemException ex) {
            return onRedisFailure(
                    zsetKey, "loadPage.zrevrange", ex, pageOneBased, size, allowDbFallbackOnRedisFailure, rankingSnapshotIdEcho);
        }
        List<Long> parsedIds = new ArrayList<>();
        for (RankingZsetEntry e : entries) {
            try {
                parsedIds.add(Long.parseLong(e.member()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (parsedIds.isEmpty()) {
            return new RankingPage(
                    List.of(), pageOneBased, size, total, totalPages, listSource, rankingSnapshotIdEcho, null);
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
        return new RankingPage(
                rows, pageOneBased, size, total, totalPages, listSource, rankingSnapshotIdEcho, null);
    }

    private RankingPage onRedisFailure(
            String key,
            String operation,
            Exception ex,
            int pageOneBased,
            int size,
            boolean allowDbFallbackOnRedisFailure,
            String rankingSnapshotIdEcho) {
        degradedCounter(operation).increment();
        try {
            MDC.put(MDC_RANKING_DEGRADED, "true");
            MDC.put(MDC_RANKING_OPERATION, operation);
            log.warn("ranking.degraded=true operation={} key={} errorClass={} message={}",
                    operation, key, ex.getClass().getSimpleName(), ex.getMessage());
            if (!allowDbFallbackOnRedisFailure || !fallbackOnRedisFailure) {
                return new RankingPage(
                        List.of(),
                        pageOneBased,
                        size,
                        0L,
                        0,
                        RankingListSource.DEGRADED_EMPTY,
                        rankingSnapshotIdEcho,
                        null);
            }
            return loadPageFromLatestProducts(pageOneBased, size);
        } finally {
            MDC.remove(MDC_RANKING_DEGRADED);
            MDC.remove(MDC_RANKING_OPERATION);
        }
    }

    /**
     * 최신 상품 목록에서 페이지를 조회한다.
     * @param pageOneBased 페이지 (1부터)
     * @param size 페이지 크기
     * @return 랭킹 페이지 결과
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
                    RankingListSource.DEGRADED_EMPTY,
                    null,
                    null
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
                RankingListSource.FALLBACK_DB_LATEST,
                null,
                null
        );
    }

    /**
     * 랭킹 디그레이더 메트릭을 증가시킨다.
     * @param operation 작업 이름
     * @return 랭킹 디그레이더 메트릭
     */
    private Counter degradedCounter(String operation) {
        return Counter.builder("ranking.degraded")
                .tag("operation", operation)
                .register(meterRegistry);
    }

    /**
     * 일간 라이브 ZSET에서 상품의 전역 순위(1-based, 점수 내림차순)를 조회한다.
     *
     * @param rankingDate 랭킹 일자
     * @param productId   상품 ID (양수)
     * @return 순위가 있으면 값, ZSET 미등록 시 empty
     * @throws CoreException {@code productId <= 0}
     */
    public OptionalLong findOneBasedDailyRank(LocalDate rankingDate, long productId) {
        return findOneBasedRank(rankingDate, productId, Optional.empty());
    }

    /**
     * 랭킹 목록과 동일한 ZSET 키에서 상품의 전역 순위(1-based)를 조회한다.
     * {@code rankingSnapshotId}가 비어 있으면 라이브 일간 키, 있으면 해당 스냅샷 키(존재 검증)를 쓴다.
     *
     * @param rankingDate              랭킹 일자
     * @param productId                상품 ID (양수)
     * @param rankingSnapshotIdRaw     스냅샷 UUID(선택)
     * @return 순위가 있으면 값, 해당 ZSET에 member 없으면 empty
     * @throws CoreException {@code productId <= 0}, 잘못된 UUID, 스냅샷 키 없음·만료
     */
    public OptionalLong findOneBasedRank(
            LocalDate rankingDate,
            long productId,
            Optional<String> rankingSnapshotIdRaw) {
        if (productId <= 0L) {
            throw new CoreException(ErrorType.BAD_REQUEST, "productId must be positive");
        }
        final String key;
        if (rankingSnapshotIdRaw.isEmpty() || rankingSnapshotIdRaw.get().isBlank()) {
            key = RankingKey.dailyAll(rankingDate);
        } else {
            String sid = normalizeRankingSnapshotId(rankingSnapshotIdRaw.get());
            key = RankingKey.snapshot(rankingDate, sid);
            if (!rankingSnapshotRepository.exists(key)) {
                throw new CoreException(ErrorType.NOT_FOUND, "rankingSnapshotId가 없거나 만료되었습니다.");
            }
        }
        String member = String.valueOf(productId);
        try {
            return rankingReadRepository.findOneBasedReverseRank(key, member);
        } catch (RedisConnectionFailureException | RedisSystemException ex) {
            degradedCounter("findOneBasedRank").increment();
            try {
                MDC.put(MDC_RANKING_DEGRADED, "true");
                MDC.put(MDC_RANKING_OPERATION, "findOneBasedRank");
                log.warn("ranking.degraded=true operation=findOneBasedRank key={} member={} errorClass={} message={}",
                        key, member, ex.getClass().getSimpleName(), ex.getMessage());
            } finally {
                MDC.remove(MDC_RANKING_DEGRADED);
                MDC.remove(MDC_RANKING_OPERATION);
            }
            return OptionalLong.empty();
        }
    }

    /**
     * 총 페이지 수를 계산한다.
     * @param total 총 아이템 수
     * @param size 페이지 크기
     * @return 총 페이지 수
     */
        private static int computeTotalPages(long total, int size) {
        if (total <= 0L || size <= 0) {
            return 0;
        }
        return (int) ((total + size - 1) / size);
    }
}
