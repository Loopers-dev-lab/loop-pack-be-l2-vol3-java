package com.loopers.domain.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * <ul>
 *   <li>ZSET member에는 ID만 저장한다. 이름·가격은 {@link ProductRepository} 등으로 배치 로드한다.</li>
 *   <li>상품 Redis String 캐시를 도입하면 동일 키로 검색·추천 등과 히트율을 공유할 수 있다(설계 §4.2.2).</li>
 *   <li>랭킹 목록 전체를 애플리케이션/Redis에 페이지 캐시하지 않는 것이 기본 권장이다(설계 §4.2.3).</li>
 * </ul>
 * Redis 접근은 {@link RankingReadRepository}에 위임한다.
 */
@Service
public class RankingQueryService {

    private static final Logger log = LoggerFactory.getLogger(RankingQueryService.class);

    private final RankingReadRepository rankingReadRepository;
    private final ProductRepository productRepository;
    private final BrandService brandService;
    private final LikeService likeService;

    public RankingQueryService(
            RankingReadRepository rankingReadRepository,
            ProductRepository productRepository,
            BrandService brandService,
            LikeService likeService) {
        this.rankingReadRepository = rankingReadRepository;
        this.productRepository = productRepository;
        this.brandService = brandService;
        this.likeService = likeService;
    }

    /**
     * 일간 랭킹 페이지 조회
     * <p>
     * Redis {@code ZREVRANGE} 인덱스 구간에 대응하며, 동일 페이지를 재조회해도 실시간 점수 변동으로 항목 집합이 달라질 수 있다.
     * {@code pageOneBased}가 마지막 슬라이스를 넘기면 빈 행 목록을 반환하고 {@code totalElements}/{@code totalPages}는 유지한다.
     * 동일 {@code score}인 member 간 순서는 {@link RankingReadRepository#findReverseRangeWithScores}가 반환하는 순서(저장소 규칙)를 따른다.
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
            log.warn("ranking degraded mode: redis unavailable in loadPage key={}", key, ex);
            return new RankingPage(List.of(), pageOneBased, size, 0L, 0);
        }
        int totalPages = computeTotalPages(total, size);
        if (total == 0L) {
            return new RankingPage(List.of(), pageOneBased, size, 0L, 0);
        }
        long startIndex = (long) (pageOneBased - 1) * size;
        if (startIndex >= total) {
            return new RankingPage(List.of(), pageOneBased, size, total, totalPages);
        }
        long endIndex = Math.min(startIndex + size - 1, total - 1);
        // ZSET에서 랭킹 목록 아이템 목록을 조회한다.
        List<RankingZsetEntry> entries = rankingReadRepository.findReverseRangeWithScores(key, startIndex, endIndex);
        // 랭킹 목록 아이템 목록을 파싱한다.
        List<Long> parsedIds = new ArrayList<>();
        for (RankingZsetEntry e : entries) {
            try {
                parsedIds.add(Long.parseLong(e.member()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (parsedIds.isEmpty()) {
            return new RankingPage(List.of(), pageOneBased, size, total, totalPages);
        }
        // 랭킹 목록 아이템 목록을 상품 목록으로 변환한다.
        Map<Long, ProductModel> productMap = productRepository.findByIdInAndNotDeletedAsMap(parsedIds);
        // 랭킹 목록 아이템 목록을 브랜드 목록으로 변환한다.
        List<Long> brandIds = productMap.values().stream()
                .map(ProductModel::getBrandId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        // 랭킹 목록 아이템 목록을 좋아요 목록으로 변환한다.
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
                    likeCount
            ));
        }
        // 랭킹 목록 아이템 목록을 페이지 결과로 변환한다.
        return new RankingPage(rows, pageOneBased, size, total, totalPages);
    }

    /**
     * 일간 랭킹 ZSET에서 상품의 전역 순위(1-based, 점수 내림차순)를 조회한다.
     * <p>
     * member 규칙은 쓰기 경로와 동일하게 {@code String.valueOf(productId)}이다.
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
            log.warn("ranking degraded mode: redis unavailable in findOneBasedDailyRank key={} member={}", key, member, ex);
            return OptionalLong.empty();
        }
    }

    /**
     * 총 페이지 수를 계산한다.
     * @param total 총 아이템 수
     * @param size 페이지 크기
     * @return 총 페이지 수 (1부터)
     */
    private static int computeTotalPages(long total, int size) {
        if (total <= 0L || size <= 0) {
            return 0;
        }
        return (int) ((total + size - 1) / size);
    }
}
