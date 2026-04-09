package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Ranking V1 API", description = "상품 랭킹 API")
public interface RankingV1ApiSpec {

    @Operation(summary = "일별 상품 랭킹 조회", description = "Redis ZSET 기반 일별 인기 상품 Top-N을 조회합니다.")
    ApiResponse<List<RankingV1Dto.RankingResponse>> getTopRankings(
        @Parameter(description = "조회 날짜 (yyyyMMdd)") String date,
        @Parameter(description = "페이지 크기") int size,
        @Parameter(description = "페이지 번호 (1부터 시작)") int page
    );
}
