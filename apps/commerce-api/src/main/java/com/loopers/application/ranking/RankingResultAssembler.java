package com.loopers.application.ranking;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.loopers.application.product.cache.ProductCacheReader;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Product;
import com.loopers.domain.ranking.RankingItem;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 랭킹 항목에 상품 정보와 좋아요 여부를 조합하여 {@link RankingPageResult}를 생성한다.
 */
@Component
@RequiredArgsConstructor
public class RankingResultAssembler {

    private final ProductCacheReader productCacheReader;
    private final LikeService likeService;

    /**
     * 랭킹 항목 목록에 상품 상세와 좋아요 여부를 결합하여 페이지 결과를 반환한다.
     *
     * @param userId       사용자 ID (비로그인 시 null)
     * @param rankingItems 랭킹 항목 목록
     * @param pageSize     페이지 정보
     * @return 조합된 랭킹 페이지 결과
     */
    public RankingPageResult assemble(Long userId, List<RankingItem> rankingItems, PageSize pageSize) {
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
