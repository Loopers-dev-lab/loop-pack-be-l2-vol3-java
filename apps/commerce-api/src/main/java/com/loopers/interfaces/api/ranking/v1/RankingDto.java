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
     * @param rankings   순위별 상품 목록
     * @param page       현재 페이지
     * @param size       페이지 크기
     * @param totalCount 전체 랭킹 상품 수
     */
    public record RankingResponse(
            List<RankedProductResponse> rankings,
            int page,
            int size,
            long totalCount
    ) {

        /**
         * UseCase 결과로부터 응답을 생성한다.
         *
         * @param result 랭킹 페이지 결과
         * @return 랭킹 응답
         */
        public static RankingResponse from(RankingPageResult result) {
            List<RankedProductResponse> rankings = result.rankings().stream()
                    .map(RankedProductResponse::from)
                    .toList();
            return new RankingResponse(rankings, result.page(), result.size(), result.totalCount());
        }
    }

    /**
     * 순위가 포함된 개별 상품 응답.
     *
     * @param rank         순위
     * @param productId    상품 ID
     * @param productName  상품명
     * @param price        가격
     * @param thumbnailUrl 썸네일 URL
     */
    public record RankedProductResponse(
            int rank,
            Long productId,
            String productName,
            Long price,
            String thumbnailUrl
    ) {

        /**
         * UseCase 결과로부터 응답을 생성한다.
         *
         * @param product 순위가 포함된 상품 결과
         * @return 개별 상품 응답
         */
        public static RankedProductResponse from(RankedProduct product) {
            return new RankedProductResponse(
                    product.rank(),
                    product.productId(),
                    product.productName(),
                    product.price(),
                    product.thumbnailUrl()
            );
        }
    }
}
