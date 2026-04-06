package com.loopers.application.ranking;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.loopers.application.product.cache.ProductCacheReader;
import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.product.Product;
import com.loopers.domain.ranking.RankingItem;
import com.loopers.domain.ranking.RankingService;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 인기 상품 랭킹을 페이지 단위로 조회합니다.
 *
 * <p>Redis Sorted Set에서 상위 상품 ID와 score를 조회한 뒤,
 * 상품 캐시 인프라를 통해 상품 정보를 매핑하여 반환합니다.</p>
 */
@UseCase
@RequiredArgsConstructor
public class ReadRankingsUseCase {

    private final RankingService rankingService;
    private final ProductCacheReader productCacheReader;

    /**
     * 랭킹 페이지를 조회한다.
     *
     * @param date     조회 날짜 (yyyyMMdd), null이면 오늘
     * @param pageSize 페이지 정보
     * @return 랭킹 페이지 결과
     */
    public RankingPageResult execute(String date, PageSize pageSize) {
        List<RankingItem> rankingItems = rankingService.readTopRanked(date, pageSize.offset(), pageSize.size());
        long totalCount = rankingService.countAll(date);

        if (rankingItems.isEmpty()) {
            return new RankingPageResult(Collections.emptyList(), pageSize.page(), pageSize.size(), totalCount);
        }

        List<Long> productIds = rankingItems.stream()
                .map(RankingItem::productId)
                .toList();
        Map<Long, Product> products = productCacheReader.readActiveProductsByIds(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<RankedProduct> rankings = rankingItems.stream()
                .filter(item -> products.containsKey(item.productId()))
                .map(item -> RankedProduct.from(item, products.get(item.productId())))
                .toList();

        return new RankingPageResult(rankings, pageSize.page(), pageSize.size(), totalCount);
    }
}
