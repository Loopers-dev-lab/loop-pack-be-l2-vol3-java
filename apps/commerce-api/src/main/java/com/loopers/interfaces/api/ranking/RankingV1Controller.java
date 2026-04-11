package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingInfo;
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

    private final RankingFacade rankingFacade;

    @GetMapping
    @Override
    public ApiResponse<List<RankingV1Dto.RankingResponse>> getTopRankings(
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "1") int page
    ) {
        String resolvedDate = date != null ? date : LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        List<RankingInfo> rankings = rankingFacade.getTopRankings(resolvedDate, page, size);
        List<RankingV1Dto.RankingResponse> response = rankings.stream()
            .map(RankingV1Dto.RankingResponse::from)
            .toList();
        return ApiResponse.success(response);
    }
}
