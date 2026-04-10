package com.loopers.interfaces.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.dto.RankingCriteria;
import com.loopers.application.ranking.dto.RankingResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.ranking.dto.RankingV1Dto;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingV1Controller implements RankingV1ApiSpec {

    private final RankingFacade rankingFacade;

    @Override
    @GetMapping
    public ApiResponse<RankingV1Dto.ListResponse> getRankings(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyyMMdd") LocalDate date,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        RankingCriteria.Search criteria = new RankingCriteria.Search(
                date != null ? date : LocalDate.now(), page, size);
        RankingResult.RankingPage rankingPage = rankingFacade.getTopRankings(criteria);
        return ApiResponse.success(RankingV1Dto.ListResponse.from(rankingPage));
    }
}
