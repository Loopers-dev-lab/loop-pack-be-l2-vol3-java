package com.loopers.application.ranking;

import com.loopers.domain.product.Product;
import com.loopers.domain.ranking.RankingItem;

/**
 * 순위가 포함된 상품 조회 결과.
 *
 * @param rank         1-based 순위
 * @param productId    상품 ID
 * @param productName  상품명
 * @param price        가격
 * @param thumbnailUrl 썸네일 URL
 * @param brandId      브랜드 ID
 * @param liked        좋아요 여부
 * @param score        가중치 기반 누적 점수
 */
public record RankedProduct(
        int rank,
        Long productId,
        String productName,
        Long price,
        String thumbnailUrl,
        Long brandId,
        boolean liked,
        double score
) {

    public static RankedProduct from(RankingItem item, Product product, boolean liked) {
        return new RankedProduct(
                item.rank(),
                product.getId(),
                product.getName().getValue(),
                product.getPrice().getAmount(),
                product.getThumbnailUrl().getValue(),
                product.getBrandId(),
                liked,
                item.score()
        );
    }
}
