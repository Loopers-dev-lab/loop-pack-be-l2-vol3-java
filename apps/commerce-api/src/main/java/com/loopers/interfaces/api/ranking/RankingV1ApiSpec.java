package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking V1 API", description = "랭킹 관련 API입니다.")
public interface RankingV1ApiSpec {

    @Operation(
        summary = "랭킹 조회",
        description = "일별 상품 랭킹을 조회합니다. date 파라미터가 없으면 오늘 날짜를 기본값으로 사용합니다."
    )
    ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
        @Parameter(description = "조회 날짜 (yyyyMMdd 형식)", example = "20250407")
        String date,
        @Parameter(description = "페이지 크기", example = "20")
        int size,
        @Parameter(description = "페이지 번호 (1부터 시작)", example = "1")
        int page
    );

    @Operation(
        summary = "시간별 랭킹 조회",
        description = "시간별 상품 랭킹을 조회합니다. hour 파라미터가 없으면 현재 시간을 기본값으로 사용합니다. TTL: 3시간"
    )
    ApiResponse<RankingV1Dto.HourlyRankingPageResponse> getHourlyRankings(
        @Parameter(description = "조회 시간 (yyyyMMddHH 형식)", example = "2025040714")
        String hour,
        @Parameter(description = "페이지 크기", example = "20")
        int size,
        @Parameter(description = "페이지 번호 (1부터 시작)", example = "1")
        int page
    );
}
