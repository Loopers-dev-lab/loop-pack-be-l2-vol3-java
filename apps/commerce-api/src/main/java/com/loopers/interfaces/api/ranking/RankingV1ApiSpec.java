package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

public interface RankingV1ApiSpec {

    @GetMapping
    ApiResponse<RankingV1Dto.RankingListResponse> getRankings(
            @RequestParam String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "daily") String period
    );
}
