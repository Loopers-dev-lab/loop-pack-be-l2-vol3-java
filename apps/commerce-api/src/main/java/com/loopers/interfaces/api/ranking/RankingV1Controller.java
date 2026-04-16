package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingPageResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.ranking.dto.RankingV1Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 랭킹 조회 API V1.
 *
 * GET /api/v1/rankings?date=yyyyMMdd&page=1&size=20
 *
 * 주의:
 * - page 는 1-based (과제 명세의 ?page=1 예시가 "첫 페이지" 를 의미)
 * - size 는 상한 — 삭제/숨김 상품이 응답에서 제외되어 실제 반환 개수가 작을 수 있음
 */
@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingV1Controller {

    private final RankingFacade rankingFacade;

    @GetMapping
    public ApiResponse<RankingV1Dto.RankingPageResponse> getDailyRanking(
            @RequestParam(value = "date", required = false) String dateStr,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        RankingPageQuery query = RankingPageQuery.of(dateStr, page, size);
        RankingPageResult result = rankingFacade.getDailyRanking(query.date(), query.page(), query.size());

        List<RankingV1Dto.RankingItemResponse> itemResponses = result.items().stream()
                .map(RankingV1Dto.RankingItemResponse::from)
                .toList();

        return ApiResponse.success(new RankingV1Dto.RankingPageResponse(
                query.formattedDate(result.effectiveDate()),
                query.page(),
                query.size(),
                result.total(),
                itemResponses
        ));
    }
}
