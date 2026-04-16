package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingInfo;
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
import java.util.Set;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/rankings")
public class RankingV1Controller implements RankingV1ApiSpec {

    private final RankingFacade rankingFacade;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Set<String> VALID_PERIODS = Set.of("daily", "weekly", "monthly");

    @GetMapping
    @Override
    public ApiResponse<RankingV1Dto.RankingPageResponse> getRankings(
            @RequestParam(defaultValue = "daily") String period,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") int page
    ) {
        if (!VALID_PERIODS.contains(period)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 기간입니다: " + period);
        }
        if (page < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 1 이상이어야 합니다.");
        }
        if (size < 1 || size > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1~100 범위여야 합니다.");
        }

        if (date == null || date.isBlank()) {
            date = LocalDate.now().format(DATE_FORMAT);
        } else {
            try {
                LocalDate.parse(date, DATE_FORMAT);
            } catch (DateTimeParseException e) {
                throw new CoreException(ErrorType.BAD_REQUEST, "날짜 형식이 올바르지 않습니다. (yyyyMMdd)");
            }
        }

        RankingInfo.RankingPageResponse info = switch (period) {
            case "weekly" -> rankingFacade.getRankingsWeekly(date, page, size);
            case "monthly" -> rankingFacade.getRankingsMonthly(date, page, size);
            default -> rankingFacade.getRankings(date, page, size);
        };

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
