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

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private final RankingFacade rankingFacade;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    @GetMapping
    @Override
    public ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
            @RequestParam(defaultValue = "daily") String period,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") int page
    ) {
        if (date == null || date.isBlank()) {
            date = LocalDate.now().format(DATE_FORMAT);
        }

        // period에 따라 데이터 소스 분기
        RankingInfo.RankingPageResponse info = switch (period) {
            case "weekly" -> rankingFacade.getRankingsWeekly(date, page, size);
            case "monthly" -> rankingFacade.getRankingsMonthly(date, page, size);
            default -> rankingFacade.getRankings(date, page, size);
        };

        return ApiResponse.success(RankingV1Dto.RankingPageResponse.from(info));
    }

    @GetMapping("/db")
    @Override
    public ApiResponse<RankingV1Dto.RankingPageResponse> getRankingsFromDB(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") int page
    ) {
        RankingInfo.RankingPageResponse info = rankingFacade.getRankingsFromDB(page, size);
        return ApiResponse.success(RankingV1Dto.RankingPageResponse.from(info));
    }
}
