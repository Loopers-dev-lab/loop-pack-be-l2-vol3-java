package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingQueryFacade;
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

    private final RankingQueryFacade rankingQueryFacade;

    @GetMapping("/top")
    public ApiResponse<RankingDto.TopRankingResponse> getTopRankings(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(RankingDto.TopRankingResponse.from(rankingQueryFacade.getTop(limit)));
    }
}
