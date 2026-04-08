package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingInfo;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.page.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
public class RankingV1Controller {

    private final RankingFacade rankingFacade;
    private final RankingService rankingService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResult<RankingV1Dto.RankingResponse>>> list(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        LocalDate queryDate = parseDate(date);
        PagedResult<RankingInfo> rankings = rankingFacade.getRankings(queryDate, page, size);

        PagedResult<RankingV1Dto.RankingResponse> response = new PagedResult<>(
                rankings.content().stream().map(RankingV1Dto.RankingResponse::from).toList(),
                rankings.page(),
                rankings.size(),
                rankings.totalElements(),
                rankings.totalPages()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/hourly")
    public ResponseEntity<ApiResponse<List<RankingRepository.RankingEntry>>> hourly(
            @RequestParam(value = "hour", required = false) String hour,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        String hourKey = parseHourKey(hour);
        List<RankingRepository.RankingEntry> entries = rankingService.getHourlyTopRankings(hourKey, size);
        return ResponseEntity.ok(ApiResponse.success(entries));
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
