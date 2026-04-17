package com.loopers.interfaces.api.ranking;

import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking V1 API", description = "실시간 랭킹 API")
public interface RankingV1ApiSpec {

    @Operation(summary = "랭킹 조회", description = "기간별 상품 랭킹을 페이징하여 조회합니다. (DAILY/WEEKLY/MONTHLY)")
    ApiResponse<RankingV1Dto.RankingListResponse> getRankings(String date, int size, int page, RankingPeriod period);
}
