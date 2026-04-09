package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.ranking.RankingEntry;

/**
 * 랭킹 응답 항목 VO — 순위 정보 + 상품 정보를 결합한다.
 */
public record RankingItemInfo(
        long rank,
        double score,
        ProductInfo product
) {

    public static RankingItemInfo of(RankingEntry entry, ProductInfo product) {
        return new RankingItemInfo(entry.rank(), entry.score(), product);
    }
}
