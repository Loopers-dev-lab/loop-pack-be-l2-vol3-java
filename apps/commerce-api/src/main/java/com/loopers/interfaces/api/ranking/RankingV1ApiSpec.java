package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Ranking V1 API", description = "랭킹 API 입니다.")
public interface RankingV1ApiSpec {

    @Operation(summary = "일별 랭킹 조회", description = "특정 날짜의 인기 상품 랭킹을 조회합니다. date 미지정 시 오늘 날짜를 사용합니다.")
    ApiResponse<RankingV1Dto.RankingPageResponse> getDailyRanking(
        String date,
        int page,
        int size
    );
}
