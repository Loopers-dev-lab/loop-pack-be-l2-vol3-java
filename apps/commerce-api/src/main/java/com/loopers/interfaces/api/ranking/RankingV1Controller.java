package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.domain.ranking.model.RankingQuery;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.ranking.dto.FindRankingListApiResDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private final RankingFacade rankingFacade;

    @Override
    @GetMapping
    public ApiResponse<FindRankingListApiResDto> getRankings(@RequestParam(defaultValue = "daily") String period,
                                                             @RequestParam String date,
                                                             @RequestParam(defaultValue = "20") int size,
                                                             @RequestParam(defaultValue = "1") int page) {
        return ApiResponse.success(FindRankingListApiResDto.from(rankingFacade.getRankings(RankingQuery.of(period, date, page, size))));
    }
}
