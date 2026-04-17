package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingInfo;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.page.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;

@RequiredArgsConstructor
@RestController
public class RankingController {

    private final RankingFacade rankingFacade;

    @GetMapping("/api/v1/rankings")
    public ApiResponse<PageResponse<RankingDto.Response>> getRankings(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyyMMdd") LocalDate date,
            @RequestParam(defaultValue = "DAILY") RankingPeriod period,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        LocalDate targetDate = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        PageResponse<RankingInfo> infos = rankingFacade.getPage(targetDate, period, page, size);
        return ApiResponse.success(infos.map(RankingDto.Response::from));
    }
}
