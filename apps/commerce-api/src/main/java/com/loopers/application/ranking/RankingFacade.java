package com.loopers.application.ranking;

import com.loopers.domain.ranking.ProductRankingRepository;
import com.loopers.domain.ranking.RankingDetailInfo;
import com.loopers.domain.ranking.RankingInfo;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RankingFacade {

    private final ProductRankingRepository productRankingRepository;

    public RankingDetailInfo getProductRanking(Long productId) {
        String dateKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return productRankingRepository.getProductDailyRanking(dateKey, productId)
            .orElse(null);
    }

    private static final int MAX_SIZE = 100;

    public List<RankingInfo> getRankings(RankingPeriod period, String date, int page, int size) {
        if (page < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "page는 1 이상이어야 합니다: " + page);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "size는 1~" + MAX_SIZE + " 범위여야 합니다: " + size);
        }
        int cursor = (page - 1) * size;
        LocalDate targetDate = resolveDate(date);

        return switch (period) {
            case DAILY -> {
                String dateKey = targetDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                yield productRankingRepository.getDailyRankings(dateKey, cursor, size);
            }
            case WEEKLY -> {
                String yearWeek = toYearWeek(targetDate);
                yield productRankingRepository.getWeeklyRankings(yearWeek, cursor, size);
            }
            case MONTHLY -> {
                String yearMonth = targetDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));
                yield productRankingRepository.getMonthlyRankings(yearMonth, cursor, size);
            }
        };
    }

    private LocalDate resolveDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "잘못된 날짜 형식입니다: " + dateStr);
        }
    }

    private String toYearWeek(LocalDate date) {
        WeekFields weekFields = WeekFields.ISO;
        int weekNumber = date.get(weekFields.weekOfWeekBasedYear());
        int year = date.get(weekFields.weekBasedYear());
        return String.format("%d-W%02d", year, weekNumber);
    }
}
