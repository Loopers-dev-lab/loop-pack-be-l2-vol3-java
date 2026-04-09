package com.loopers.application.ranking;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.loopers.application.product.cache.ProductCacheReader;
import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Product;
import com.loopers.domain.ranking.RankingItem;
import com.loopers.domain.ranking.RankingService;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 인기 상품 랭킹을 페이지 단위로 조회한다.
 */
@UseCase
@RequiredArgsConstructor
public class ReadRankingsUseCase {

    private final RankingService rankingService;
    private final ProductCacheReader productCacheReader;
    private final LikeService likeService;

    /**
     * @param userId   사용자 ID (비로그인 시 null)
     * @param date     조회 날짜 (yyyyMMdd), null이면 오늘
     * @param pageSize 페이지 정보
     * @return 랭킹 페이지 결과 (상품 정보, brandId, 좋아요 여부 포함)
     */
    public RankingPageResult execute(Long userId, String date, PageSize pageSize) {
        List<RankingItem> rankingItems = rankingService.readTopRanked(date, pageSize.offset(), pageSize.size());

        if (rankingItems.isEmpty()) {
            return new RankingPageResult(Collections.emptyList(), pageSize.page(), pageSize.size());
        }

        List<Long> productIds = rankingItems.stream()
                .map(RankingItem::productId)
                .toList();
        Map<Long, Product> products = productCacheReader.readActiveProductsByIds(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Set<Long> likedProductIds = likeService.getLikedProductIds(userId, productIds);

        List<RankedProduct> rankings = rankingItems.stream()
                .filter(item -> products.containsKey(item.productId()))
                .map(item -> RankedProduct.from(
                        item, products.get(item.productId()), likedProductIds.contains(item.productId())))
                .toList();

        return new RankingPageResult(rankings, pageSize.page(), pageSize.size());
    }
}
