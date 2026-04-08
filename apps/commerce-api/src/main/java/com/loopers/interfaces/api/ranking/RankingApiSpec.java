package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Rankings", description = "랭킹 API")
public interface RankingApiSpec {

    @Operation(summary = "일간 인기상품 랭킹 조회", description = "날짜별 랭킹 상위 N개의 상품 정보를 반환합니다.")
    @GetMapping
    ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
        @RequestParam(required = false) String date,
        @RequestParam(required = false, defaultValue = "20") int size,
        @RequestParam(required = false, defaultValue = "1") int page
    );
}
