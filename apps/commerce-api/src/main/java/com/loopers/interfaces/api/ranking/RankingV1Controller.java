package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingInfo;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.interfaces.api.ApiResponse;
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

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private static final int MAX_SIZE = 100;
    private final RankingFacade rankingFacade;

    @GetMapping
    @Override
    public ApiResponse<List<RankingV1Dto.RankingResponse>> getTopRankings(
        @RequestParam(defaultValue = "daily") String period,
        @RequestParam(required = false) String date,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "1") int page
    ) {
        if (page < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 1 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1~" + MAX_SIZE + " 범위여야 합니다.");
        }

        RankingPeriod rankingPeriod = parseRankingPeriod(period);
        LocalDate resolvedDate = resolveDate(date);

        List<RankingInfo> rankings = switch (rankingPeriod) {
            case DAILY -> rankingFacade.getTopRankings(resolvedDate.format(DateTimeFormatter.BASIC_ISO_DATE), page, size);
            case WEEKLY -> rankingFacade.getWeeklyRankings(resolvedDate, page, size);
            case MONTHLY -> rankingFacade.getMonthlyRankings(resolvedDate, page, size);
        };

        List<RankingV1Dto.RankingResponse> response = rankings.stream()
            .map(RankingV1Dto.RankingResponse::from)
            .toList();
        return ApiResponse.success(response);
    }

    private RankingPeriod parseRankingPeriod(String period) {
        try {
            return RankingPeriod.valueOf(period.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "period는 daily, weekly, monthly 중 하나여야 합니다.");
        }
    }

    private LocalDate resolveDate(String date) {
        if (date == null) {
            return LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        }
        try {
            return LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date 형식은 yyyyMMdd여야 합니다.");
        }
    }
}
