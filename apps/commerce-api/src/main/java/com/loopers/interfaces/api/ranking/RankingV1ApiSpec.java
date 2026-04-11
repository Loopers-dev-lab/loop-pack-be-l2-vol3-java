package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking V1 API", description = "상품 랭킹 API")
public interface RankingV1ApiSpec {

    @Operation(summary = "랭킹 조회", description = "날짜별 상품 랭킹을 조회합니다.")
    ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
            @Parameter(description = "조회 날짜 (yyyyMMdd), 미지정 시 오늘") String date,
            @Parameter(description = "페이지 크기 (기본 20)") int size,
            @Parameter(description = "페이지 번호 (1부터, 기본 1)") int page
    );

    @Operation(summary = "DB 기반 랭킹 조회", description = "product_metrics 테이블에서 가중치 합산 점수로 랭킹을 조회합니다. (ZSET 성능 비교용)")
    ApiResponse<RankingV1Dto.RankingPageResponse> getRankingsFromDB(
            @Parameter(description = "페이지 크기 (기본 20)") int size,
            @Parameter(description = "페이지 번호 (1부터, 기본 1)") int page
    );
}
