package com.loopers.domain.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        long total = rankingReadRepository.count(key);
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
