package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.ProductRankInfo;
import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingFacade rankingFacade;

    @GetMapping
    @Override
    public ApiResponse<RankingV1Dto.RankingListResponse> getRankings(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") int page
    ) {
        String dateKey = date != null ? date : LocalDate.now().format(DATE_FORMAT);
        List<RankingInfo> rankings = rankingFacade.getRankings(dateKey, page, size);
        return ApiResponse.success(RankingV1Dto.RankingListResponse.from(rankings));
    }

    @GetMapping("/products/{productId}")
    @Override
    public ApiResponse<RankingV1Dto.ProductRankResponse> getProductRank(
            @PathVariable Long productId,
            @RequestParam(required = false) String date
    ) {
        String dateKey = date != null ? date : LocalDate.now().format(DATE_FORMAT);
        ProductRankInfo rankInfo = rankingFacade.getProductRankInfo(productId, dateKey);
        return ApiResponse.success(RankingV1Dto.ProductRankResponse.from(rankInfo));
    }
}
