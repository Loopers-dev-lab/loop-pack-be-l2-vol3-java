package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking V1 API", description = "상품 랭킹 API")
public interface RankingV1ApiSpec {

    @Operation(summary = "랭킹 조회", description = "날짜별 상품 인기 랭킹을 조회합니다.")
    ApiResponse<RankingV1Dto.RankingListResponse> getRankings(String date, int size, int page);
}
