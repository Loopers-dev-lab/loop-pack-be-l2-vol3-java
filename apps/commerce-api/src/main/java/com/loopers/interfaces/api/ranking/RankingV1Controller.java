package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingPageResult;
import com.loopers.domain.ranking.RankingType;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.ranking.dto.RankingV1Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller {

    private final RankingFacade rankingFacade;
    private final Clock clock;

    @GetMapping
    public ApiResponse<List<RankingV1Dto.RankingResponse>> getRankings(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyyMMdd") LocalDate date,
            @RequestParam(defaultValue = "DAILY") RankingType rankingType,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") int page
    ) {
        LocalDate targetDate = resolveDate(date, rankingType);
        RankingPageResult result = rankingFacade.getRankings(targetDate, rankingType, page, size);

        List<RankingV1Dto.RankingResponse> responses = result.items().stream()
                                                             .map(RankingV1Dto.RankingResponse::from)
                                                             .toList();

        return ApiResponse.success(responses);
    }

    private LocalDate resolveDate(LocalDate date, RankingType rankingType) {
        if (date != null) {
            return date;
        }
        if (rankingType == RankingType.DAILY) {
            return LocalDate.now(clock);
        }
        return null; // WEEKLY/MONTHLY: null이면 Facade에서 최신 rank_date 조회
    }
}
