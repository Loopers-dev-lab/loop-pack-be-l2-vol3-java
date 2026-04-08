package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingItemInfo;
import com.loopers.domain.product.ProductStatus;

import java.util.List;

/**
 * 랭킹 API 응답 DTO
 */
public class RankingResponse {

    /** 랭킹 항목 — 상품 정보가 aggregation되어 제공 */
    public record RankingItem(
            int rank,
            Long productId,
            String productName,
            int basePrice,
            String brandName,
            int likeCount,
            String stockStatus,
            double score
    ) {
        public static RankingItem from(RankingItemInfo info) {
            return new RankingItem(
                    info.rank(),
                    info.product().id(),
                    info.product().name(),
                    info.product().basePrice(),
                    info.brandName(),
                    info.product().likeCount(),
                    toStockStatus(info.product().status()),
                    info.score()
            );
        }
    }

    /** 랭킹 페이지 응답 */
    public record RankingPageResponse(
            List<RankingItem> rankings,
            PageInfo paging
    ) {}

    public record PageInfo(
            int page,
            int size,
            long totalCount,
            boolean hasNext
    ) {}

    private static String toStockStatus(ProductStatus status) {
        return switch (status) {
            case ACTIVE -> "IN_STOCK";
            case SOLDOUT -> "SOLD_OUT";
            default -> "UNAVAILABLE";
        };
    }
}
