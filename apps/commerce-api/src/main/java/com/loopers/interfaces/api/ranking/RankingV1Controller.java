package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingInfo;
import com.loopers.interfaces.api.ApiResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingFacade rankingFacade;

    @GetMapping
    public ApiResponse<List<RankingV1Dto.RankingResponse>> getRankings(
        @RequestParam String date,
        @RequestParam(defaultValue = "daily") String period,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        LocalDate localDate = LocalDate.parse(date, DATE_FORMATTER);
        List<RankingInfo> rankings = rankingFacade.getRankings(localDate, period, size, page);
        List<RankingV1Dto.RankingResponse> response = rankings.stream()
            .map(RankingV1Dto.RankingResponse::from)
            .toList();
        return ApiResponse.success(response);
    }
}
