package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.MvRankingPage;
import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingInfo;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
@Validated
public class RankingV1Controller {

    private final RankingFacade rankingFacade;

    @GetMapping
    public ResponseEntity<ApiResponse<RankingV1Dto.PeriodRankingResponse>> list(
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "date", required = false) String date,
            @Min(value = 0, message = "page는 0 이상이어야 합니다")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Min(value = 1, message = "size는 1 이상이어야 합니다")
            @Max(value = 100, message = "size는 100 이하여야 합니다")
            @RequestParam(value = "size", defaultValue = "20") int size) {

        RankingPeriod parsedPeriod = RankingPeriod.fromOrDefault(period);
        LocalDate queryDate = parseDate(date);
        MvRankingPage rankings = rankingFacade.getRankings(parsedPeriod, queryDate, page, size);

        return ResponseEntity.ok(ApiResponse.success(RankingV1Dto.PeriodRankingResponse.from(rankings)));
    }

    @GetMapping("/hourly")
    public ResponseEntity<ApiResponse<List<RankingV1Dto.RankingResponse>>> hourly(
            @RequestParam(value = "hour", required = false) String hour,
            @Min(value = 1, message = "size는 1 이상이어야 합니다")
            @Max(value = 100, message = "size는 100 이하여야 합니다")
            @RequestParam(value = "size", defaultValue = "10") int size) {

        String hourKey = parseHourKey(hour);
        List<RankingInfo> rankings = rankingFacade.getHourlyRankings(hourKey, size);
        List<RankingV1Dto.RankingResponse> response = rankings.stream()
                .map(RankingV1Dto.RankingResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String parseHourKey(String hour) {
        if (hour != null && hour.matches("\\d{10}")) {
            return hour;
        }
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            return LocalDate.now();
        }
    }
}
