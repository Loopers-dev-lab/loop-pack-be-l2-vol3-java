package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingFacade.RankingPageResult;
import com.loopers.interfaces.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 랭킹 API
 *
 * Redis ZSET 기반 실시간 인기 상품 랭킹을 제공한다.
 * 상품 정보가 aggregation되어 단순 ID가 아닌 상품 상세와 함께 반환된다.
 */
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingController {

    private final RankingFacade rankingFacade;

    public RankingController(RankingFacade rankingFacade) {
        this.rankingFacade = rankingFacade;
    }

    /**
     * 랭킹 페이지 조회
     *
     * @param date 조회 날짜 (yyyyMMdd). 미지정 시 오늘(KST)
     * @param size 페이지 크기 (default: 20)
     * @param page 페이지 번호, 1-based (default: 1)
     */
    @GetMapping
    public ApiResponse<RankingResponse.RankingPageResponse> getRankings(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") int page
    ) {
        RankingPageResult result = rankingFacade.getRankings(date, page, size);

        List<RankingResponse.RankingItem> items = result.items().stream()
                .map(RankingResponse.RankingItem::from)
                .toList();

        return ApiResponse.success(new RankingResponse.RankingPageResponse(
                items,
                new RankingResponse.PageInfo(result.page(), result.size(), result.totalCount(), result.hasNext())
        ));
    }
}
