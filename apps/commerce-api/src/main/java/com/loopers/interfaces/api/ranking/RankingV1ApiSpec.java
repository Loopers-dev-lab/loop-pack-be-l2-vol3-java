package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;

@Tag(name = "Ranking V1 API", description = "랭킹 API 입니다.")
public interface RankingV1ApiSpec {

    @Operation(
        summary = "랭킹 조회",
        description = "일간 상품 랭킹을 조회합니다. 상품 정보가 Aggregation 되어 제공됩니다."
    )
    ApiResponse<Page<RankingV1Dto.RankingResponse>> getRankings(
        @Parameter(description = "조회할 날짜 (yyyyMMdd, 미입력 시 오늘)") String date,
        @Parameter(description = "페이지 크기 (기본값: 20)") int size,
        @Parameter(description = "페이지 번호 (0-based, 기본값: 0)") int page
    );
}
