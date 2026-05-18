package com.loopers.infrastructure.ranking.mv;

import com.loopers.domain.ranking.mv.MvProductRankMonthly;
import com.loopers.domain.ranking.mv.MvProductRankWeekly;
import com.loopers.domain.ranking.mv.MvRankingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MvRankingRepositoryAdapter implements MvRankingRepository {

    private final MvProductRankWeeklyJpaRepository weeklyJpaRepository;
    private final MvProductRankMonthlyJpaRepository monthlyJpaRepository;

    @Override
    public List<MvProductRankWeekly> findWeeklyRankings(LocalDate aggregatedAt, int page, int size) {
        return weeklyJpaRepository.findByAggregatedAtOrderByRankAsc(aggregatedAt, PageRequest.of(page, size));
    }

    @Override
    public List<MvProductRankMonthly> findMonthlyRankings(LocalDate aggregatedAt, int page, int size) {
        return monthlyJpaRepository.findByAggregatedAtOrderByRankAsc(aggregatedAt, PageRequest.of(page, size));
    }
}
