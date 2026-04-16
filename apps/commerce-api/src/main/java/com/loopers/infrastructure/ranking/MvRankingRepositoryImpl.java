package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.MvRankingRepository;
import com.loopers.domain.ranking.RankedProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MvRankingRepositoryImpl implements MvRankingRepository {

    private final MvProductRankWeeklyJpaRepository weeklyRepository;
    private final MvProductRankMonthlyJpaRepository monthlyRepository;

    @Override
    public List<RankedProduct> findWeeklyRanking(LocalDate periodStart, LocalDate periodEnd, int offset, int size) {
        return weeklyRepository.findByPeriodStartAndPeriodEndOrderByRankPositionAsc(
                periodStart, periodEnd, PageRequest.of(offset / size, size)
        ).stream().map(mv -> new RankedProduct(mv.getProductId(), mv.getScore())).toList();
    }

    @Override
    public List<RankedProduct> findMonthlyRanking(LocalDate periodStart, LocalDate periodEnd, int offset, int size) {
        return monthlyRepository.findByPeriodStartAndPeriodEndOrderByRankPositionAsc(
                periodStart, periodEnd, PageRequest.of(offset / size, size)
        ).stream().map(mv -> new RankedProduct(mv.getProductId(), mv.getScore())).toList();
    }

    @Override
    public long countWeeklyRanking(LocalDate periodStart, LocalDate periodEnd) {
        return weeklyRepository.countByPeriodStartAndPeriodEnd(periodStart, periodEnd);
    }

    @Override
    public long countMonthlyRanking(LocalDate periodStart, LocalDate periodEnd) {
        return monthlyRepository.countByPeriodStartAndPeriodEnd(periodStart, periodEnd);
    }
}
