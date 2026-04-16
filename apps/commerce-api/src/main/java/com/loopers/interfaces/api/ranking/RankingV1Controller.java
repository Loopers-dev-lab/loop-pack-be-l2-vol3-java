package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingPageInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingV1Controller {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingFacade rankingFacade;

    @GetMapping
    public ApiResponse<RankingV1Dto.PageResponse> getRankings(
            @RequestParam(defaultValue = "daily") String period,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String targetDate = (date != null && !date.isBlank()) ? date : LocalDate.now().format(DATE_FORMAT);
        RankingPageInfo pageInfo = rankingFacade.getRankings(period, targetDate, page, size);
        return ApiResponse.success(RankingV1Dto.PageResponse.from(pageInfo));
    }
}
