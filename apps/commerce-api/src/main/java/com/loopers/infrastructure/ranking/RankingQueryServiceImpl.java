package com.loopers.infrastructure.ranking;

import com.loopers.application.ranking.RankingQueryService;
import com.loopers.domain.PageResult;
import com.loopers.domain.ranking.MonthlyRankingRepository;
import com.loopers.domain.ranking.PeriodRankingRepository;
import com.loopers.domain.ranking.ProductRanking;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.WeeklyRankingRepository;
import com.loopers.support.redis.RankingKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class RankingQueryServiceImpl implements RankingQueryService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("uuuuMMdd");

    private final RankingRepository rankingRepository;
    private final WeeklyRankingRepository weeklyRankingRepository;
    private final MonthlyRankingRepository monthlyRankingRepository;

    @Override
    public PageResult<ProductRanking> getRanking(RankingPeriod period, LocalDate baseDate, int page, int size) {
        return switch (period) {
            case DAILY -> getDailyRankingFromRedis(baseDate.format(DAY_FORMAT), page, size);
            case WEEKLY -> getPeriodRanking(weeklyRankingRepository, toYearWeek(baseDate), page, size);
            case MONTHLY -> getPeriodRanking(monthlyRankingRepository, toYearMonth(baseDate), page, size);
        };
    }

    @Override
    public Optional<Long> getProductDailyRank(Long productId, String date) {
        try {
            String key = RankingKeyConstants.dayKey(date);
            return rankingRepository.getRank(key, productId);
        } catch (Exception e) {
            log.warn("[Ranking] Redis 순위 조회 실패, empty 반환: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private PageResult<ProductRanking> getDailyRankingFromRedis(String date, int page, int size) {
        try {
            String key = RankingKeyConstants.dayKey(date);
            long start = (long) page * size;
            long stop = start + size - 1;

            List<ProductRanking> rankings = rankingRepository.getTopN(key, start, stop);
            long totalElements = rankingRepository.getTotalCount(key);
            int totalPages = (int) Math.ceil((double) totalElements / size);

            return new PageResult<>(rankings, page, size, totalElements, totalPages);
        } catch (Exception e) {
            log.warn("[Ranking] Redis 조회 실패, 빈 결과 반환: {}", e.getMessage());
            return new PageResult<>(Collections.emptyList(), page, size, 0, 0);
        }
    }

    private PageResult<ProductRanking> getPeriodRanking(
        PeriodRankingRepository repository, String periodKey, int page, int size
    ) {
        // 주간/월간은 "정확성 > 실시간성" 원칙. Daily(Redis) 의 graceful degradation 과 달리
        // DB 조회 실패는 그대로 전파하여 사용자/운영자에게 장애를 숨기지 않는다.
        long offset = (long) page * size;
        List<ProductRanking> rankings = repository.findTopN(periodKey, offset, size);
        long totalElements = repository.countByPeriod(periodKey);
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResult<>(rankings, page, size, totalElements, totalPages);
    }

    private static String toYearWeek(LocalDate date) {
        int year = date.get(IsoFields.WEEK_BASED_YEAR);
        int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return String.format("%04d-W%02d", year, week);
    }

    private static String toYearMonth(LocalDate date) {
        return String.format("%04d-%02d", date.getYear(), date.getMonthValue());
    }
}
