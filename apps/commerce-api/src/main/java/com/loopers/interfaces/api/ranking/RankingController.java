package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class RankingController {

    private static final int DEFAULT_SIZE = 20;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final RankingFacade rankingFacade;

    @GetMapping("/api/v1/rankings")
    public ApiResponse<RankingDto.RankingListResponse> getRankings(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") int page
    ) {
        String rankingDate = date != null ? date : LocalDate.now().format(DATE_FORMAT);
        int zeroBasedPage = page - 1;

        List<RankingInfo> rankings = rankingFacade.getTopRankings(rankingDate, zeroBasedPage, size);
        List<RankingDto.RankingResponse> responses = rankings.stream()
                .map(RankingDto.RankingResponse::from)
                .toList();

        return ApiResponse.success(new RankingDto.RankingListResponse(responses, page, size));
    }

    @GetMapping("/api/v1/rankings/hourly")
    public ApiResponse<RankingDto.RankingListResponse> getHourlyRankings(
            @RequestParam(required = false) String hour,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") int page
    ) {
        String rankingHour = hour != null ? hour : LocalDateTime.now().format(HOUR_FORMAT);
        int zeroBasedPage = page - 1;

        List<RankingInfo> rankings = rankingFacade.getHourlyTopRankings(rankingHour, zeroBasedPage, size);
        List<RankingDto.RankingResponse> responses = rankings.stream()
                .map(RankingDto.RankingResponse::from)
                .toList();

        return ApiResponse.success(new RankingDto.RankingListResponse(responses, page, size));
    }
}
