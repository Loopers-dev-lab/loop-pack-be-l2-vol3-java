package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingPageResult;
import com.loopers.application.ranking.WeeklyRankingFacade;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.ranking.dto.RankingV1Dto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 주간 랭킹 조회 API V1.
 *
 * GET /api/v1/rankings/weekly?date=yyyyMMdd&page=1&size=20
 *
 * date  : MV 테이블의 base_date (batch 실행일 - 1일). 생략 시 KST 어제 날짜 기준.
 * page  : 1-based 페이지 번호. 기본값 1. 0 이하이면 1로 보정.
 * size  : 페이지 크기. 기본값 20. 0 이하이면 20, 100 초과이면 100으로 보정.
 *
 * 응답의 date 필드는 실제 조회에 사용된 base_date 를 yyyyMMdd 형식으로 반환한다.
 * 요청 date 를 생략하면 응답 date 는 어제 날짜가 된다.
 */
@RestController
@RequestMapping("/api/v1/rankings/weekly")
@RequiredArgsConstructor
public class WeeklyRankingV1Controller {

    private final WeeklyRankingFacade weeklyRankingFacade;

    @GetMapping
    public ApiResponse<RankingV1Dto.RankingPageResponse> getWeeklyRanking(
            @RequestParam(value = "date", required = false) String dateStr,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        RankingPageQuery query = RankingPageQuery.of(dateStr, page, size);
        RankingPageResult result = weeklyRankingFacade.getWeeklyRanking(query.date(), query.page(), query.size());

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
