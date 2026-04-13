package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.ranking.dto.FindRankingListApiResDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking V1 API", description = "상품 랭킹 조회 API 입니다.")
public interface RankingV1ApiSpec {

    @Operation(summary = "일간 랭킹 조회", description = "일간 상품 랭킹을 조회합니다.")
    ApiResponse<FindRankingListApiResDto> getRankings(
            @Parameter(description = "조회 날짜 (yyyyMMdd)", required = true) String date,
            @Parameter(description = "페이지 크기") int size,
            @Parameter(description = "페이지 번호 (1부터)") int page
    );
}
