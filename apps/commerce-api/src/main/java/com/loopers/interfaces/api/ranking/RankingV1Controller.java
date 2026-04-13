package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingDto;
import com.loopers.application.ranking.RankingFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RequiredArgsConstructor
@RestController
public class RankingV1Controller {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingFacade rankingFacade;

    @GetMapping("/api/v1/rankings")
    public ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "0") int page
    ) {
        String resolvedDate = (date != null) ? date : todayDate();
        List<RankingDto.RankingItemInfo> rankings = rankingFacade.getRankings(resolvedDate, page, size);
        return ApiResponse.success(RankingV1Dto.RankingPageResponse.of(rankings, page, size));
    }

    private String todayDate() {
        return LocalDate.now(ZoneId.of("Asia/Seoul")).format(DATE_FORMAT);
    }
}
