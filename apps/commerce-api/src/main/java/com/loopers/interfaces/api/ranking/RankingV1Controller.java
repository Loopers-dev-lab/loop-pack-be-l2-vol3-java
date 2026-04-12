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

    /**
     * 날짜 기반 상품 랭킹 목록을 페이징으로 조회한다.
     * - date 미입력 시 오늘 날짜 기준
     * - page는 0-based
     * - 인증 불필요 (공개 API)
     */
    @GetMapping
    public ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        String targetDate = (date == null || date.isBlank())
            ? LocalDate.now().format(DATE_FORMATTER)
            : date;

        List<RankingInfo> rankings = rankingFacade.findRankings(targetDate, page, size);
        List<RankingV1Dto.RankingResponse> content = rankings.stream()
            .map(RankingV1Dto.RankingResponse::from)
            .toList();

        return ApiResponse.success(new RankingV1Dto.RankingPageResponse(content, targetDate, page, size));
    }
}
