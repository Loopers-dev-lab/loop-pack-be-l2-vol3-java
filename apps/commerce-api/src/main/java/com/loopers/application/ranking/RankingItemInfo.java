package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRow;

import java.math.BigDecimal;

/**
 * 랭킹 아이템 응답 DTO
 *
 * @param rank 랭킹 순위
 * @param productId 상품 ID
 * @param score 랭킹 점수
 * @param productName 상품 이름
 * @param price 상품 가격
 * @param brandId 브랜드 ID
 * @param brandName 브랜드 이름
 * @param likeCount 좋아요 수
 */
public record RankingItemInfo(
        int rank,
        long productId,
        double score,
        String productName,
        BigDecimal price,
        long brandId,
        String brandName,
        long likeCount
) {
    public static RankingItemInfo from(RankingRow row) {
        return new RankingItemInfo(
                row.rank(),
                row.productId(),
                row.score(),
                row.productName(),
                row.price(),
                row.brandId(),
                row.brandName(),
                row.likeCount()
        );
    }
}
