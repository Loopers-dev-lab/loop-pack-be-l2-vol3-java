package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking V1 API", description = "랭킹 조회 API 입니다.")
public interface RankingV1ApiSpec {

    @Operation(
            summary = "일간 랭킹 조회",
            description = "지정 날짜의 인기 상품 랭킹을 조회합니다. date 미입력 시 오늘 기준."
    )
    ApiResponse<RankingV1Dto.RankingListResponse> getDailyRanking(
            @Parameter(description = "조회 날짜 (yyyyMMdd, 기본값: 오늘)") String date,
            @Parameter(description = "페이지 번호 (0부터 시작, 기본값: 0)") int page,
            @Parameter(description = "페이지 크기 (기본값: 20)") int size
    );

    @Operation(
            summary = "시간별 랭킹 조회",
            description = "지정 날짜·시간대의 실시간 인기 상품 랭킹을 조회합니다. date 미입력 시 오늘, hour 미입력 시 현재 시각 기준."
    )
    ApiResponse<RankingV1Dto.RankingListResponse> getHourlyRanking(
            @Parameter(description = "조회 날짜 (yyyyMMdd, 기본값: 오늘)") String date,
            @Parameter(description = "조회 시간 (0~23, 기본값: 현재 시각)") Integer hour,
            @Parameter(description = "페이지 번호 (0부터 시작, 기본값: 0)") int page,
            @Parameter(description = "페이지 크기 (기본값: 20)") int size
    );
}
