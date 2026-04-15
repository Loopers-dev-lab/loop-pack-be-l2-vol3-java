package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingFacade;
import com.loopers.application.ranking.RankingInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class RankingController {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final RankingFacade rankingFacade;

    @GetMapping("/api/v1/rankings")
    public ApiResponse<RankingDto.RankingListResponse> getRankings(
            @RequestParam(defaultValue = "daily") String period,
            @RequestParam(required = false) String date,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size,
            @RequestParam(defaultValue = "1") int page
    ) {
        int validatedSize = validatePageSize(size);
        int zeroBasedPage = validatePage(page) - 1;

        List<RankingInfo> rankings = switch (period) {
            case "daily" -> {
                String rankingDate = validateDate(date);
                yield rankingFacade.getTopRankings(rankingDate, zeroBasedPage, validatedSize);
            }
            case "weekly" -> {
                String yearWeek = toYearWeek(date);
                yield rankingFacade.getWeeklyTopRankings(yearWeek, zeroBasedPage, validatedSize);
            }
            case "monthly" -> {
                String yearMonth = toYearMonth(date);
                yield rankingFacade.getMonthlyTopRankings(yearMonth, zeroBasedPage, validatedSize);
            }
            default -> throw new CoreException(ErrorType.BAD_REQUEST, "period는 daily, weekly, monthly 중 하나여야 합니다.");
        };

        List<RankingDto.RankingResponse> responses = rankings.stream()
                .map(RankingDto.RankingResponse::from)
                .toList();

        return ApiResponse.success(new RankingDto.RankingListResponse(responses, page, validatedSize));
    }

    @GetMapping("/api/v1/rankings/hourly")
    public ApiResponse<RankingDto.RankingListResponse> getHourlyRankings(
            @RequestParam(required = false) String hour,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size,
            @RequestParam(defaultValue = "1") int page
    ) {
        String rankingHour = validateHour(hour);
        int validatedSize = validatePageSize(size);
        int zeroBasedPage = validatePage(page) - 1;

        List<RankingInfo> rankings = rankingFacade.getHourlyTopRankings(rankingHour, zeroBasedPage, validatedSize);
        List<RankingDto.RankingResponse> responses = rankings.stream()
                .map(RankingDto.RankingResponse::from)
                .toList();

        return ApiResponse.success(new RankingDto.RankingListResponse(responses, page, validatedSize));
    }

    private String toYearWeek(String date) {
        LocalDate targetDate = date != null ? parseDate(date) : LocalDate.now();
        int year = targetDate.get(IsoFields.WEEK_BASED_YEAR);
        int week = targetDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%d-W%02d", year, week);
    }

    private String toYearMonth(String date) {
        LocalDate targetDate = date != null ? parseDate(date) : LocalDate.now();
        return String.format("%d-%02d", targetDate.getYear(), targetDate.getMonthValue());
    }

    private LocalDate parseDate(String date) {
        try {
            return LocalDate.parse(date, DATE_FORMAT);
        } catch (Exception e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date는 yyyyMMdd 형식이어야 합니다.");
        }
    }

    private int validatePage(int page) {
        if (page < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 1 이상이어야 합니다.");
        }
        return page;
    }

    private int validatePageSize(int size) {
        if (size < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1 이상이어야 합니다.");
        }
        if (size > MAX_PAGE_SIZE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 " + MAX_PAGE_SIZE + " 이하여야 합니다.");
        }
        return size;
    }

    private String validateDate(String date) {
        String rankingDate = date != null ? date : LocalDate.now().format(DATE_FORMAT);
        try {
            LocalDate.parse(rankingDate, DATE_FORMAT);
            return rankingDate;
        } catch (Exception e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "date는 yyyyMMdd 형식이어야 합니다.");
        }
    }

    private String validateHour(String hour) {
        String rankingHour = hour != null ? hour : LocalDateTime.now().format(HOUR_FORMAT);
        try {
            HOUR_FORMAT.parse(rankingHour);
            return rankingHour;
        } catch (Exception e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "hour는 yyyyMMddHH 형식이어야 합니다.");
        }
    }
}
