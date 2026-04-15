package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private final RankingFacade rankingFacade;

    @GetMapping
    @Override
    public ApiResponse<Page<RankingV1Dto.RankingResponse>> getRankings(
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "0") int page
    ) {
        Page<RankingInfo> rankings = rankingFacade.getRankings(date, page, size);
        Page<RankingV1Dto.RankingResponse> response = rankings.map(RankingV1Dto.RankingResponse::from);
        return ApiResponse.success(response);
    }
}
