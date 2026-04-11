package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;

import java.util.List;

public class RankingV1Dto {

    /** 랭킹 항목 1건의 API 응답 형식 */
    public record RankingItemResponse(
            int rank,
            double score,
            Long productId,
            String productName,
            String brandName,
            int price,
            String imageUrl
    ) {
        /** RankingInfo.RankingItem → API 응답 DTO 변환 */
        public static RankingItemResponse from(RankingInfo.RankingItem item) {
            return new RankingItemResponse(
                    item.rank(),
                    item.score(),
                    item.productId(),
                    item.productName(),
                    item.brandName(),
                    item.price(),
                    item.imageUrl()
            );
        }
    }

    /** 페이징된 랭킹 목록의 API 응답 형식 */
    public record RankingPageResponse(
            List<RankingItemResponse> rankings,
            int page,
            int size
    ) {
        /** RankingInfo.RankingPageResponse → API 응답 DTO 변환 */
        public static RankingPageResponse from(RankingInfo.RankingPageResponse info) {
            List<RankingItemResponse> items = info.rankings().stream()
                    .map(RankingItemResponse::from)
                    .toList();
            return new RankingPageResponse(items, info.page(), info.size());
        }
    }
}
