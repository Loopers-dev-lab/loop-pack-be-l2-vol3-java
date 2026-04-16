package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingApp;
import com.loopers.application.ranking.RankingCursorResult;
import com.loopers.application.ranking.RankingPageResult;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingV1Controller implements RankingV1ApiSpec {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingApp rankingApp;

    @GetMapping
    @Override
    public ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> getRankingByOffset(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        RankingPeriod rankingPeriod = RankingPeriod.fromString(period);
        LocalDate targetDate = parseDateOrToday(date);
        RankingPageResult result = rankingApp.getTopN(rankingPeriod, targetDate, page, size);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (result.periodKey() != null) {
            builder.header("X-Ranking-Period-Key", result.periodKey());
            builder.header("X-Ranking-Is-Fallback", String.valueOf(result.isFallback()));
        }
        if (result.publishedVersion() != null) {
            builder.header("X-Ranking-Version", String.valueOf(result.publishedVersion()));
        }
        return builder.body(ApiResponse.success(RankingV1Dto.RankingPageResponse.from(result)));
    }

    @GetMapping("/cursor")
    @Override
    public ResponseEntity<ApiResponse<RankingV1Dto.RankingCursorResponse>> getRankingByCursor(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Double cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        LocalDate targetDate = parseDateOrToday(date);
        RankingCursorResult result = rankingApp.getByCursor(targetDate, cursor, size);
        return ResponseEntity.ok(ApiResponse.success(RankingV1Dto.RankingCursorResponse.from(result)));
    }

    @GetMapping("/hourly")
    @Override
    public ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> getHourlyRankingByOffset(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Integer hour,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        LocalDate targetDate = parseDateOrToday(date);
        int targetHour = parseHourOrNow(hour);
        RankingPageResult result = rankingApp.getHourlyTopN(targetDate, targetHour, page, size);
        return ResponseEntity.ok(ApiResponse.success(RankingV1Dto.RankingPageResponse.from(result)));
    }

    @GetMapping("/hourly/cursor")
    @Override
    public ResponseEntity<ApiResponse<RankingV1Dto.RankingCursorResponse>> getHourlyRankingByCursor(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Integer hour,
            @RequestParam(required = false) Double cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        LocalDate targetDate = parseDateOrToday(date);
        int targetHour = parseHourOrNow(hour);
        RankingCursorResult result = rankingApp.getHourlyByCursor(targetDate, targetHour, cursor, size);
        return ResponseEntity.ok(ApiResponse.success(RankingV1Dto.RankingCursorResponse.from(result)));
    }

    private LocalDate parseDateOrToday(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(date, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "지원하지 않는 date 형식: " + date + " (expected: yyyyMMdd)");
        }
    }

    private int parseHourOrNow(Integer hour) {
        if (hour == null) {
            return LocalTime.now().getHour();
        }
        return hour;
    }
}
