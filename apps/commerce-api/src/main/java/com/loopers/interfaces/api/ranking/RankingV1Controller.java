package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.PeriodRankingResult;
import com.loopers.application.ranking.RankingPeriod;
import com.loopers.application.ranking.RankingQueryService;
import com.loopers.application.ranking.RankingResult;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter HOURLY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final RankingQueryService rankingQueryService;

    @GetMapping
    @Override
    public ApiResponse<RankingV1Dto.PeriodRankingPageResponse> getRankings(
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "DAILY") RankingPeriod period,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "1") int page
    ) {
        LocalDate queryDate = (date != null)
            ? LocalDate.parse(date, DATE_FORMATTER)
            : LocalDate.now();

        int offset = (page - 1) * size;
        List<PeriodRankingResult> results = rankingQueryService.getPeriodRankings(queryDate, period, size, offset);
        long totalCount = rankingQueryService.getPeriodTotalCount(queryDate, period);

        List<RankingV1Dto.PeriodRankingResponse> rankings = results.stream()
            .map(RankingV1Dto.PeriodRankingResponse::from)
            .toList();

        // 기간 시작/종료일 계산
        LocalDate periodStart = calculatePeriodStart(queryDate, period);
        LocalDate periodEnd = calculatePeriodEnd(queryDate, period);

        return ApiResponse.success(RankingV1Dto.PeriodRankingPageResponse.of(
            rankings,
            queryDate.format(DATE_FORMATTER),
            period,
            periodStart.format(DATE_FORMATTER),
            periodEnd.format(DATE_FORMATTER),
            page,
            size,
            totalCount
        ));
    }

    private LocalDate calculatePeriodStart(LocalDate date, RankingPeriod period) {
        return switch (period) {
            case DAILY -> date;
            case WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTHLY -> YearMonth.from(date).atDay(1);
        };
    }

    private LocalDate calculatePeriodEnd(LocalDate date, RankingPeriod period) {
        return switch (period) {
            case DAILY -> date;
            case WEEKLY -> date.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            case MONTHLY -> YearMonth.from(date).atEndOfMonth();
        };
    }

    @GetMapping("/hourly")
    @Override
    public ApiResponse<RankingV1Dto.HourlyRankingPageResponse> getHourlyRankings(
        @RequestParam(required = false) String hour,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "1") int page
    ) {
        LocalDateTime queryDateTime = (hour != null)
            ? LocalDateTime.parse(hour, HOURLY_FORMATTER)
            : LocalDateTime.now();

        int offset = (page - 1) * size;
        List<RankingResult> results = rankingQueryService.getHourlyRankings(queryDateTime, size, offset);
        long totalCount = rankingQueryService.getHourlyTotalCount(queryDateTime);

        List<RankingV1Dto.RankingResponse> rankings = results.stream()
            .map(RankingV1Dto.RankingResponse::from)
            .toList();

        return ApiResponse.success(RankingV1Dto.HourlyRankingPageResponse.of(
            rankings,
            queryDateTime.format(HOURLY_FORMATTER),
            page,
            size,
            totalCount
        ));
    }
}
