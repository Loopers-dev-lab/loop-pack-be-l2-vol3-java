package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rankings")
public class RankingController {

    private final RankingFacade rankingFacade;

    @GetMapping
    public ApiResponse<RankingDto.PagedRankingResponse> getRankings(
        @RequestParam(defaultValue = "daily") String scope,
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Long memberId
    ) {
        RankingDto.PagedRankingResponse response = rankingFacade.getRankings(scope, date, page, size, memberId);
        return ApiResponse.success(response);
    }
}
