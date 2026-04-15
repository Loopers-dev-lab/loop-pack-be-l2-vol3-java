package com.loopers.interfaces.api.ranking.v1;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 인기 상품 랭킹 API 명세.
 */
@Tag(name = "Ranking V1 API", description = "인기 상품 랭킹 API 입니다.")
public interface RankingV1ApiSpec {

    @Operation(
            summary = "일간 인기 상품 랭킹 조회 API",
            description = "Redis Sorted Set 기반의 일간 인기 상품 랭킹을 페이지 단위로 조회합니다."
    )
    ApiResponse<RankingDto.RankingResponse> getDailyRankings(Long userId, String date, int page, int size);

    @Operation(
            summary = "시간 단위 인기 상품 랭킹 조회 API",
            description = "Redis Sorted Set 기반의 시간 단위 인기 상품 랭킹을 페이지 단위로 조회합니다."
    )
    ApiResponse<RankingDto.RankingResponse> getHourlyRankings(Long userId, String datetime, int page, int size);

    @Operation(
            summary = "주간 인기 상품 랭킹 조회 API",
            description = "배치 집계 기반의 주간 인기 상품 랭킹을 페이지 단위로 조회합니다."
    )
    ApiResponse<RankingDto.RankingResponse> getWeeklyRankings(Long userId, String date, int page, int size);
}
