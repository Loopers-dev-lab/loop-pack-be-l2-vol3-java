package com.loopers.interfaces.api.ranking.v1;

import java.util.List;

import com.loopers.application.ranking.RankedProduct;
import com.loopers.application.ranking.RankingPageResult;

/**
 * 랭킹 API 요청/응답 DTO.
 */
public class RankingDto {

    /**
     * 랭킹 페이지 응답.
     *
     * @param rankings 순위별 상품 목록
     * @param page     현재 페이지
     * @param size     페이지 크기
     */
    public record RankingResponse(
            List<RankedProductResponse> rankings,
            int page,
            int size
    ) {

        public static RankingResponse from(RankingPageResult result) {
            List<RankedProductResponse> rankings = result.rankings().stream()
                    .map(RankedProductResponse::from)
                    .toList();
            return new RankingResponse(rankings, result.page(), result.size());
        }
    }

    public record RankedProductResponse(
            int rank,
            Long productId,
            String productName,
            Long price,
            String thumbnailUrl,
            Long brandId,
            boolean liked
    ) {

        public static RankedProductResponse from(RankedProduct product) {
            return new RankedProductResponse(
                    product.rank(),
                    product.productId(),
                    product.productName(),
                    product.price(),
                    product.thumbnailUrl(),
                    product.brandId(),
                    product.liked()
            );
        }
    }
}
