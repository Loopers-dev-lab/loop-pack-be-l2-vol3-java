package com.loopers.application.ranking;

import java.util.List;

public class RankingInfo {

    /** 랭킹 항목 1건: 순위 + 점수 + 상품/브랜드 정보 */
    public record RankingItem(
            int rank,
            double score,
            Long productId,
            String productName,
            String brandName,
            int price,
            String imageUrl
    ) {}

    /** 페이징된 랭킹 응답 */
    public record RankingPageResponse(
            List<RankingItem> rankings,
            int page,
            int size
    ) {}
}
