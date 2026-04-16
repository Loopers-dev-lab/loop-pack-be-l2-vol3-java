package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.MonthlyRankingFacade;
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
 * 월간 랭킹 조회 API V1.
 *
 * GET /api/v1/rankings/monthly?date=yyyyMMdd&page=1&size=20
 *
 * date  : MV 테이블의 base_date (batch 실행일 - 1일). 생략 시 KST 어제 날짜 기준.
 * page  : 1-based 페이지 번호. 기본값 1. 0 이하이면 1로 보정.
 * size  : 페이지 크기. 기본값 20. 0 이하이면 20, 100 초과이면 100으로 보정.
 *
 * WeeklyRankingV1Controller 와 구조가 동일하며, 대상 Facade 와 엔드포인트만 다르다.
 */
@RestController
@RequestMapping("/api/v1/rankings/monthly")
@RequiredArgsConstructor
public class MonthlyRankingV1Controller {

    private final MonthlyRankingFacade monthlyRankingFacade;

    @GetMapping
    public ApiResponse<RankingV1Dto.RankingPageResponse> getMonthlyRanking(
            @RequestParam(value = "date", required = false) String dateStr,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        RankingPageQuery query = RankingPageQuery.of(dateStr, page, size);
        RankingPageResult result = monthlyRankingFacade.getMonthlyRanking(query.date(), query.page(), query.size());

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
