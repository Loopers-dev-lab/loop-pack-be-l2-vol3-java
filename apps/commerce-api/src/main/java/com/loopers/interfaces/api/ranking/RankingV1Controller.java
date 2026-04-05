package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingApp;
import com.loopers.application.ranking.RankingCursorResult;
import com.loopers.application.ranking.RankingPageResult;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingV1Controller implements RankingV1ApiSpec {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingApp rankingApp;

    @GetMapping
    @Override
    public ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> getRankingByOffset(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        LocalDate targetDate = parseDateOrToday(date);
        RankingPageResult result = rankingApp.getTopN(targetDate, page, size);
        return ResponseEntity.ok(ApiResponse.success(RankingV1Dto.RankingPageResponse.from(result)));
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

    private LocalDate parseDateOrToday(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now();
        }
        return LocalDate.parse(date, DATE_FORMATTER);
    }
}
