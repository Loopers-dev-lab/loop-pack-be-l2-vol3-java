package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingPageInfo;
import com.loopers.application.ranking.RankingQueryService;
import com.loopers.application.ranking.HourlyRankingPageInfo;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter BASIC_HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final RankingQueryService rankingQueryService;

    @GetMapping
    @Override
    public ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
        @RequestParam(defaultValue = "DAILY") String period,
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "1") int page
    ) {
        LocalDate targetDate = date == null || date.isBlank() ? null : LocalDate.parse(date, BASIC_DATE);
        RankingPeriod rankingPeriod = RankingPeriod.valueOf(period.toUpperCase());

        RankingPageInfo pageInfo = switch (rankingPeriod) {
            case DAILY -> rankingQueryService.getDailyRanking(targetDate, page, size);
            case WEEKLY -> rankingQueryService.getWeeklyRanking(targetDate, page, size);
            case MONTHLY -> rankingQueryService.getMonthlyRanking(targetDate, page, size);
        };

        return ApiResponse.success(RankingV1Dto.RankingPageResponse.from(pageInfo));
    }

    @GetMapping("/hourly")
    @Override
    public ApiResponse<RankingV1Dto.HourlyRankingPageResponse> getHourlyRankings(
        @RequestParam(required = false) String hour,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "1") int page
    ) {
        LocalDateTime targetHour = hour == null || hour.isBlank() ? null : LocalDateTime.parse(hour, BASIC_HOUR);
        HourlyRankingPageInfo pageInfo = rankingQueryService.getHourlyRanking(targetHour, page, size);
        return ApiResponse.success(RankingV1Dto.HourlyRankingPageResponse.from(pageInfo));
    }
}
