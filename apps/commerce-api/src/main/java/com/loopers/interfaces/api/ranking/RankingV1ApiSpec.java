package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Ranking V1 API", description = "상품 랭킹 API")
public interface RankingV1ApiSpec {

    @Operation(
        summary = "상품 랭킹 조회",
        description = "기간별 인기 상품 랭킹을 조회합니다. daily=Redis ZSET, weekly/monthly=배치 집계 MV 테이블."
    )
    ApiResponse<List<RankingV1Dto.RankingResponse>> getTopRankings(
        @Parameter(description = "집계 기간 (daily, weekly, monthly)") String period,
        @Parameter(description = "조회 날짜 (yyyyMMdd)") String date,
        @Parameter(description = "페이지 크기") int size,
        @Parameter(description = "페이지 번호 (1부터 시작)") int page
    );
}
