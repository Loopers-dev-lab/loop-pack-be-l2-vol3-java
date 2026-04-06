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
 * @param score        가중치 기반 누적 점수
 */
public record RankedProduct(
        int rank,
        Long productId,
        String productName,
        Long price,
        String thumbnailUrl,
        double score
) {

    /**
     * 랭킹 항목과 상품 정보를 조합하여 생성한다.
     *
     * @param item    랭킹 항목
     * @param product 상품 도메인 객체
     * @return 순위가 포함된 상품 결과
     */
    public static RankedProduct from(RankingItem item, Product product) {
        return new RankedProduct(
                item.rank(),
                product.getId(),
                product.getName().getValue(),
                product.getPrice().getAmount(),
                product.getThumbnailUrl().getValue(),
                item.score()
        );
    }
}
