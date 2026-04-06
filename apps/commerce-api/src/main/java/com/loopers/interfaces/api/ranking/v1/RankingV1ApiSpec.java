package com.loopers.interfaces.api.ranking.v1;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 랭킹 API Swagger 스펙.
 */
@Tag(name = "Ranking V1 API", description = "인기 상품 랭킹 API 입니다.")
public interface RankingV1ApiSpec {

    @Operation(
            summary = "인기 상품 랭킹 조회 API",
            description = "Redis Sorted Set 기반의 인기 상품 랭킹을 페이지 단위로 조회합니다."
    )
    ApiResponse<RankingDto.RankingResponse> getRankings(String date, int page, int size);

}
