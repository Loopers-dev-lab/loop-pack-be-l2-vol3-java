package com.loopers.interfaces.api.ranking.v1;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.ranking.RankingPageResult;
import com.loopers.application.ranking.ReadHourlyRankingsUseCase;
import com.loopers.application.ranking.ReadDailyRankingsUseCase;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.support.page.PageSize;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rankings")
public class RankingV1Api implements RankingV1ApiSpec {

    private final ReadDailyRankingsUseCase readRankingsUseCase;
    private final ReadHourlyRankingsUseCase readHourlyRankingsUseCase;

    @GetMapping("/daily")
    @Override
    public ApiResponse<RankingDto.RankingResponse> getDailyRankings(
            @LoginUser Long userId,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        RankingPageResult result = readRankingsUseCase.execute(userId, date, PageSize.withMaxSize(page, size));
        return ApiResponse.success(RankingDto.RankingResponse.from(result));
    }

    @GetMapping("/hourly")
    @Override
    public ApiResponse<RankingDto.RankingResponse> getHourlyRankings(
            @LoginUser Long userId,
            @RequestParam(required = false) String datetime,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        RankingPageResult result = readHourlyRankingsUseCase.execute(userId, datetime, PageSize.withMaxSize(page, size));
        return ApiResponse.success(RankingDto.RankingResponse.from(result));
    }
}
