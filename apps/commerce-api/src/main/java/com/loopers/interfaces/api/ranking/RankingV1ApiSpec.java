package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking V1 API", description = "실시간 랭킹 API 입니다.")
public interface RankingV1ApiSpec {

    @Operation(
        summary = "랭킹 페이지 조회",
        description = "일자별 랭킹을 페이지 단위로 조회합니다. page는 1부터 시작합니다."
    )
    ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
        @Parameter(description = "조회 일자 (yyyyMMdd). 미입력 시 오늘", example = "20260405") String date,
        @Parameter(description = "페이지 크기", example = "20") int size,
        @Parameter(description = "페이지 번호(1-base)", example = "1") int page
    );

    @Operation(
        summary = "시간별 랭킹 페이지 조회",
        description = "시간별 랭킹을 페이지 단위로 조회합니다. hour는 yyyyMMddHH 형식이며, 미입력 시 현재 시간대 기준입니다."
    )
    ApiResponse<RankingV1Dto.HourlyRankingPageResponse> getHourlyRankings(
        @Parameter(description = "조회 시각 (yyyyMMddHH). 미입력 시 현재 시간", example = "2026040523") String hour,
        @Parameter(description = "페이지 크기", example = "20") int size,
        @Parameter(description = "페이지 번호(1-base)", example = "1") int page
    );
}
