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

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private final RankingFacade rankingFacade;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    @GetMapping
    @Override
    public ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") int page
    ) {
        // date 미지정 시 오늘 날짜를 기본값으로 사용
        if (date == null || date.isBlank()) {
            date = LocalDate.now().format(DATE_FORMAT);
        }

        // Facade에서 ZSET 조회 + 상품/브랜드 Aggregation 수행
        RankingInfo.RankingPageResponse info = rankingFacade.getRankings(date, page, size);
        // application 레이어 Info → interfaces 레이어 DTO 변환
        return ApiResponse.success(RankingV1Dto.RankingPageResponse.from(info));
    }

    @GetMapping("/db")
    @Override
    public ApiResponse<RankingV1Dto.RankingPageResponse> getRankingsFromDB(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") int page
    ) {
        RankingInfo.RankingPageResponse info = rankingFacade.getRankingsFromDB(page, size);
        return ApiResponse.success(RankingV1Dto.RankingPageResponse.from(info));
    }
}
