package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    private final RankingRepository rankingRepository;

    // ---- DAILY (Redis ZSET) ----

    /**
     * 지정 날짜의 Top-N 랭킹을 페이지 단위로 조회한다.
     */
    public List<RankingRepository.RankingEntry> getTopRankings(LocalDate date, int page, int size) {
        long start = (long) page * size;
        long end = start + size - 1;
        return rankingRepository.getTopRankings(date, start, end);
    }

    /**
     * 특정 상품의 순위를 조회한다. (1-based 반환, 없으면 null)
     */
    public Long getRank(LocalDate date, Long productId) {
        Long zeroBasedRank = rankingRepository.getRank(date, productId);
        return zeroBasedRank != null ? zeroBasedRank + 1 : null;
    }

    public long getTotalCount(LocalDate date) {
        return rankingRepository.getTotalCount(date);
    }

    public List<RankingRepository.RankingEntry> getHourlyTopRankings(String hourKey, int size) {
        return rankingRepository.getHourlyTopRankings(hourKey, 0, size - 1);
    }

    // ---- WEEKLY (MV) ----

    public List<RankingRepository.MvRankingEntry> getWeeklyTop(LocalDate baseDate, int page, int size) {
        return rankingRepository.getWeeklyTop(baseDate, page, size);
    }

    public long getWeeklyTotal(LocalDate baseDate) {
        return rankingRepository.getWeeklyTotal(baseDate);
    }

    public Optional<LocalDateTime> getWeeklyAggregatedAt(LocalDate baseDate) {
        return rankingRepository.getWeeklyAggregatedAt(baseDate);
    }

    // ---- MONTHLY (MV) ----

    public List<RankingRepository.MvRankingEntry> getMonthlyTop(YearMonth yearMonth, int page, int size) {
        return rankingRepository.getMonthlyTop(yearMonth, page, size);
    }

    public long getMonthlyTotal(YearMonth yearMonth) {
        return rankingRepository.getMonthlyTotal(yearMonth);
    }

    public Optional<LocalDateTime> getMonthlyAggregatedAt(YearMonth yearMonth) {
        return rankingRepository.getMonthlyAggregatedAt(yearMonth);
    }
}
