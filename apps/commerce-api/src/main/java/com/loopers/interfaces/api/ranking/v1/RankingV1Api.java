package com.loopers.interfaces.api.ranking.v1;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.ranking.RankingPageResult;
import com.loopers.application.ranking.ReadRankingsUseCase;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

/**
 * 인기 상품 랭킹 조회 API.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rankings")
public class RankingV1Api implements RankingV1ApiSpec {

    private final ReadRankingsUseCase readRankingsUseCase;

    @GetMapping
    @Override
    public ApiResponse<RankingDto.RankingResponse> getRankings(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        RankingPageResult result = readRankingsUseCase.execute(date, PageSize.withMaxSize(page, size));
        return ApiResponse.success(RankingDto.RankingResponse.from(result));
    }
}
