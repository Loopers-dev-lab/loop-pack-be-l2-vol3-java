package com.loopers.infrastructure.ranking;

import com.loopers.domain.model.ranking.RankingPeriodKeyResolver;
import com.loopers.domain.repository.PeriodRankingRepository;
import com.loopers.domain.repository.RankingRepository.RankedProduct;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class JpaPeriodRankingRepository implements PeriodRankingRepository {

    private final ProductRankWeeklyJpaRepository weeklyRepository;
    private final ProductRankMonthlyJpaRepository monthlyRepository;

    public JpaPeriodRankingRepository(ProductRankWeeklyJpaRepository weeklyRepository,
                                      ProductRankMonthlyJpaRepository monthlyRepository) {
        this.weeklyRepository = weeklyRepository;
        this.monthlyRepository = monthlyRepository;
    }

    @Override
    public List<RankedProduct> getWeeklyRankings(LocalDate date, int offset, int size) {
        String yearWeek = RankingPeriodKeyResolver.toYearWeek(date);
        PageRequest pageable = PageRequest.of(offset / size, size);
        return weeklyRepository.findByYearWeekOrderByRanking(yearWeek, pageable).stream()
                .map(e -> new RankedProduct(e.getProductId(), e.getScore(), e.getRanking() - 1L))
                .toList();
    }

    @Override
    public long getWeeklyTotalCount(LocalDate date) {
        return weeklyRepository.countByYearWeek(RankingPeriodKeyResolver.toYearWeek(date));
    }

    @Override
    public List<RankedProduct> getMonthlyRankings(LocalDate date, int offset, int size) {
        String yearMonth = RankingPeriodKeyResolver.toYearMonth(date);
        PageRequest pageable = PageRequest.of(offset / size, size);
        return monthlyRepository.findByYearMonthOrderByRanking(yearMonth, pageable).stream()
                .map(e -> new RankedProduct(e.getProductId(), e.getScore(), e.getRanking() - 1L))
                .toList();
    }

    @Override
    public long getMonthlyTotalCount(LocalDate date) {
        return monthlyRepository.countByYearMonth(RankingPeriodKeyResolver.toYearMonth(date));
    }
}
