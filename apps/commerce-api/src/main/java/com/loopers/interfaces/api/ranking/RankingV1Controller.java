package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingInfo;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/v1/rankings")
@RequiredArgsConstructor
public class RankingV1Controller {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RankingFacade rankingFacade;
    private final Clock clock;

    // Query

    @GetMapping
    public ApiResponse<RankingV1Dto.PageResponse> getRankings(
            @RequestParam(defaultValue = "DAILY") RankingPeriod period,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-Loopers-UserId", required = false) Long userId
    ) {
        LocalDate targetDate = parseDate(date);
        validatePaging(page, size);

        RankingInfo result = rankingFacade.getRankings(period, targetDate, page, size, userId);
        return ApiResponse.success(RankingV1Dto.PageResponse.from(result));
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now(clock);
        }
        try {
            return LocalDate.parse(date, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "날짜 형식이 올바르지 않습니다. yyyyMMdd 형식을 사용하세요");
        }
    }

    private void validatePaging(int page, int size) {
        if (page < 0 || page > 49) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 0~49 사이여야 합니다");
        }
        if (size < 1 || size > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1~100 사이여야 합니다");
        }
    }
}
