package com.loopers.interfaces.ranking;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.ranking.dto.RankingV1Dto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;

@Tag(name = "Ranking V1 API", description = "상품 랭킹 API 입니다.")
public interface RankingV1ApiSpec {

    @Operation(
            summary = "인기상품 랭킹 조회",
            description = "날짜별 인기상품 랭킹을 페이지 단위로 조회합니다.")
    ApiResponse<RankingV1Dto.ListResponse> getRankings(
            @Parameter(description = "조회 날짜 (yyyyMMdd, 미입력 시 오늘)")
            LocalDate date,
            @Parameter(description = "페이지 번호 (1부터 시작)")
            int page,
            @Parameter(description = "페이지당 항목 수")
            int size);
}
