package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingFacade rankingFacade;

    @GetMapping
    @Override
    public ApiResponse<RankingV1Dto.RankingListResponse> getDailyRanking(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size)
    {
        LocalDate targetDate = (date != null) ? LocalDate.parse(date, DATE_FORMAT) : LocalDate.now();
        var result = rankingFacade.findDailyRanking(targetDate, page, size);
        return ApiResponse.success(RankingV1Dto.RankingListResponse.from(result));
    }

    @GetMapping("/hourly")
    @Override
    public ApiResponse<RankingV1Dto.RankingListResponse> getHourlyRanking(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Integer hour,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size)
    {
        LocalDate targetDate = (date != null) ? LocalDate.parse(date, DATE_FORMAT) : LocalDate.now();
        int targetHour = (hour != null) ? hour : LocalTime.now().getHour();
        var result = rankingFacade.findHourlyRanking(targetDate, targetHour, page, size);
        return ApiResponse.success(RankingV1Dto.RankingListResponse.from(result));
    }

    @GetMapping("/weekly")
    @Override
    public ApiResponse<RankingV1Dto.RankingListResponse> getWeeklyRanking(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size)
    {
        var result = rankingFacade.findWeeklyRanking(date, page, size);
        return ApiResponse.success(RankingV1Dto.RankingListResponse.from(result));
    }

    @GetMapping("/monthly")
    @Override
    public ApiResponse<RankingV1Dto.RankingListResponse> getMonthlyRanking(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size)
    {
        var result = rankingFacade.findMonthlyRanking(date, page, size);
        return ApiResponse.success(RankingV1Dto.RankingListResponse.from(result));
    }
}
