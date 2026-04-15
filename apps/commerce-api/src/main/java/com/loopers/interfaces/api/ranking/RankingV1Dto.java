package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingItemInfo;
import com.loopers.application.ranking.RankingListInfo;
import com.loopers.domain.ranking.RankingSnapshotCreateResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public class RankingV1Dto {

    @Schema(description = "랭킹 한 행. `rank`는 해당 `date` 키의 ZSET에서의 전역 순위(1-based). 동점·실시간 변동은 design §4.2.6 참고.")
    public record ItemResponse(
            @Schema(description = "전역 순위(1-based). 오프셋: (page-1)*size + 행 인덱스")
            int rank,
            long productId,
            @Schema(description = "ZSET score(동일 score 시 Redis member 규칙으로 상대 순서 결정). FALLBACK_LATEST 시 0")
            double score,
            String name,
            BigDecimal price,
            long brandId,
            String brandName,
            long likeCount,
            @Schema(description = "재고 수량(품절 UI용). 랭킹에서 품절 필터는 하지 않음.")
            int stockQuantity
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
                    item.likeCount(),
                    item.stockQuantity()
            );
        }
    }

    /**
     * 랭킹 목록 응답 DTO (오프셋 페이징 메타 포함, design §4.2.6).
     */
    @Schema(description = "오프셋 페이징 결과. `rankingSnapshotId`로 조회 시 순서가 스냅샷 시점에 고정됨.")
    public record ListResponse(
            @Schema(description = "현재 페이지 행 목록(빈 배열 가능: 요청 page가 범위를 벗어난 경우 등)")
            List<ItemResponse> content,
            @Schema(description = "요청한 페이지(1-based)")
            int page,
            @Schema(description = "요청한 페이지 크기")
            int size,
            @Schema(description = "ZSET 전체 원소 수(ZCARD). FALLBACK_LATEST 시 DB 페이지 total")
            long totalElements,
            @Schema(description = "총 페이지 수(ceil(totalElements/size), totalElements=0이면 0)")
            int totalPages,
            @Schema(description = "REDIS·REDIS_SNAPSHOT·FALLBACK_LATEST·DEGRADED")
            String dataSource,
            @Schema(description = "스냅샷 조회 시 echo, 라이브 조회면 null")
            String rankingSnapshotId
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
                    result.totalPages(),
                    result.dataSource(),
                    result.rankingSnapshotId()
            );
        }
    }

    @Schema(description = "POST /rankings/snapshots 응답")
    public record SnapshotCreateResponse(
            @Schema(description = "이후 GET ?rankingSnapshotId= 에 사용")
            String rankingSnapshotId,
            long totalElements,
            long ttlSeconds
    ) {
        public static SnapshotCreateResponse from(RankingSnapshotCreateResult result) {
            return new SnapshotCreateResponse(
                    result.snapshotId(), result.totalElements(), result.ttlSeconds());
        }
    }
}
