package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingItemInfo;
import com.loopers.application.ranking.RankingListInfo;

import java.math.BigDecimal;
import java.util.List;

public class RankingV1Dto {

    public record ItemResponse(
            int rank,
            long productId,
            double score,
            String name,
            BigDecimal price,
            long brandId,
            String brandName,
            long likeCount
    ) {
        /**
         * 랭킹 아이템을 응답 DTO로 변환한다.
         *
         * @param item 랭킹 아이템
         * @rank 랭킹 순위
         * @productId 상품 ID
         * @score 랭킹 점수
         * @productName 상품 이름
         * @price 상품 가격
         * @brandId 브랜드 ID
         * @brandName 브랜드 이름
         * @likeCount 좋아요 수
         * @return 랭킹 아이템 응답 DTO
         */
        public static ItemResponse from(RankingItemInfo item) {
            return new ItemResponse(
                    item.rank(),
                    item.productId(),
                    item.score(),
                    item.productName(),
                    item.price(),
                    item.brandId(),
                    item.brandName(),
                    item.likeCount()
            );
        }
    }

    /**
     * 랭킹 목록 응답 DTO
     */
    public record ListResponse(
            List<ItemResponse> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        /**
         * 랭킹 목록 결과를 응답 DTO로 변환한다.
         *
         * @param result 랭킹 목록 결과
         * @return 랭킹 목록 응답 DTO
         * @content 랭킹 아이템 목록
         * @page 페이지
         * @size 페이지 크기
         * @totalElements 총 아이템 수
         * @totalPages 총 페이지 수
         */
        public static ListResponse from(RankingListInfo result) {
            List<ItemResponse> content = result.items().stream()
                    .map(ItemResponse::from)
                    .toList();
            return new ListResponse(
                    content,
                    result.page(),
                    result.size(),
                    result.totalElements(),
                    result.totalPages()
            );
        }
    }
}
