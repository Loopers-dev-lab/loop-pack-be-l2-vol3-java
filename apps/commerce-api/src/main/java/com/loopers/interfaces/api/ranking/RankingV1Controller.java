package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingListResultInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rankings")
@Validated
public class RankingV1Controller implements RankingV1ApiSpec {

    private final RankingFacade rankingFacade;

    public RankingV1Controller(RankingFacade rankingFacade) {
        this.rankingFacade = rankingFacade;
    }

    /**
     * 일간 인기 상품 랭킹 조회 API 구현
     *
     * @param date 일자 yyyyMMdd (선택, 기본: 오늘(Asia/Seoul))
     * @param page 페이지 (1부터)
     * @param size 페이지 크기
     * @return 일간 인기 상품 랭킹 조회 결과
     */
    @GetMapping
    @Override
    public ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> getRankings(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        RankingListResultInfo listResult = rankingFacade.getRankings(date, page, size);
        return ResponseEntity.ok(ApiResponse.success(RankingV1Dto.ListResponse.from(listResult)));
    }
}
