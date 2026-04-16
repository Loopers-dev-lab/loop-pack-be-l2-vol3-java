package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingFacade rankingFacade;

    @GetMapping
    public ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "1") int page
    ) {
        String rankingDate = (date != null) ? date : LocalDate.now().format(DATE_FORMATTER);

        List<RankingInfo> rankings = rankingFacade.getRankings(rankingDate, page, size);
        long totalElements = rankingFacade.getTotalCount(rankingDate);

        List<RankingV1Dto.RankingItemResponse> content = rankings.stream()
            .map(RankingV1Dto.RankingItemResponse::from)
            .toList();

        return ApiResponse.success(new RankingV1Dto.RankingPageResponse(content, totalElements, page, size));
    }
}
