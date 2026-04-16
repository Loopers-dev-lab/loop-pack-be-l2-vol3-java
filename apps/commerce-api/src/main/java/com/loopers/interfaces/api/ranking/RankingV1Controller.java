package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingQueryService;
import com.loopers.application.ranking.RankingResult;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    public ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "1") int page
    ) {
        LocalDate queryDate = (date != null)
            ? LocalDate.parse(date, DATE_FORMATTER)
            : LocalDate.now();

        int offset = (page - 1) * size;
        List<RankingResult> results = rankingQueryService.getRankings(queryDate, size, offset);
        long totalCount = rankingQueryService.getTotalCount(queryDate);

        List<RankingV1Dto.RankingResponse> rankings = results.stream()
            .map(RankingV1Dto.RankingResponse::from)
            .toList();

        return ApiResponse.success(RankingV1Dto.RankingPageResponse.of(
            rankings,
            queryDate.format(DATE_FORMATTER),
            page,
            size,
            totalCount
        ));
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
