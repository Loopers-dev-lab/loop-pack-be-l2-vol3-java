package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingItemInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.ranking.dto.RankingV1Dto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 랭킹 조회 API V1.
 *
 * <pre>
 * GET /api/v1/rankings?date=yyyyMMdd&page=1&size=20
 * </pre>
 *
 * 주의:
 * - `page` 는 1-based (과제 명세의 `?page=1` 예시가 "첫 페이지" 를 의미)
 * - `size` 는 상한 — 삭제/숨김 상품이 응답에서 제외되어 실제 반환 개수가 작을 수 있음
 */
@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingV1Controller {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final RankingFacade rankingFacade;

    @GetMapping
    public ApiResponse<RankingV1Dto.RankingPageResponse> getDailyRanking(
            @RequestParam(value = "date", required = false) String dateStr,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        LocalDate date = parseDate(dateStr);
        int safePage = Math.max(page, DEFAULT_PAGE);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);

        List<RankingItemInfo> items = rankingFacade.getDailyRanking(date, safePage, safeSize);
        long total = rankingFacade.getDailyTotal(date);
        // 응답 헤더의 date 필드도 facade 와 동일한 KST clock 기준으로 결정
        LocalDate effectiveDate = date != null ? date : rankingFacade.today();

        List<RankingV1Dto.RankingItemResponse> itemResponses = items.stream()
                .map(RankingV1Dto.RankingItemResponse::from)
                .toList();

        RankingV1Dto.RankingPageResponse response = new RankingV1Dto.RankingPageResponse(
                effectiveDate.format(YYYYMMDD),
                safePage,
                safeSize,
                total,
                itemResponses
        );
        return ApiResponse.success(response);
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr, YYYYMMDD);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date 는 yyyyMMdd 형식이어야 합니다: " + dateStr, e);
        }
    }
}
