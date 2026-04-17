package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingWithProduct;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingFacade rankingFacade;

    @Override
    @GetMapping
    public ApiResponse<RankingV1Dto.RankingListResponse> getRankings(
        @RequestParam(defaultValue = "") String date,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "DAILY") RankingPeriod period
    ) {
        LocalDate targetDate = date.isBlank() ? LocalDate.now() : LocalDate.parse(date, DATE_FORMAT);
        List<RankingWithProduct> rankings = rankingFacade.getTopRankings(targetDate, page, size, period);
        List<RankingV1Dto.RankingResponse> responses = rankings.stream()
            .map(RankingV1Dto.RankingResponse::from)
            .toList();
        return ApiResponse.success(new RankingV1Dto.RankingListResponse(
            targetDate.format(DATE_FORMAT), page, size, period.name(), responses
        ));
    }
}
