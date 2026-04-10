package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingQueryFacade;
import com.loopers.application.ranking.RankingWindow;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rankings")
public class RankingController {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final RankingQueryFacade rankingQueryFacade;

    @GetMapping
    public ApiResponse<RankingDto.TopRankingResponse> getRankings(
            @RequestParam(defaultValue = "DAILY") RankingWindow window,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyyMMdd") LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyyMMddHH") LocalDateTime hour,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (window == RankingWindow.HOURLY) {
            LocalDateTime metricHour = hour != null
                    ? hour
                    : LocalDateTime.now(KOREA_ZONE).withMinute(0).withSecond(0).withNano(0);
            return ApiResponse.success(RankingDto.TopRankingResponse.from(
                    window.name(),
                    metricHour.format(HOUR_FORMATTER),
                    page,
                    size,
                    rankingQueryFacade.getHourlyPage(metricHour, page, size)
            ));
        }
        LocalDate metricDate = date != null ? date : LocalDate.now(KOREA_ZONE);
        return ApiResponse.success(RankingDto.TopRankingResponse.from(
                window.name(),
                metricDate.format(DateTimeFormatter.BASIC_ISO_DATE),
                page,
                size,
                rankingQueryFacade.getDailyPage(metricDate, page, size)
        ));
    }
}
